package com.scorbutics.maven.service;

import com.scorbutics.maven.model.Deployment;
import com.scorbutics.maven.service.event.watcher.files.*;
import com.scorbutics.maven.service.event.watcher.files.observer.*;
import com.scorbutics.maven.service.filesystem.*;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.plugin.logging.Log;
import com.scorbutics.maven.exception.FileWatcherException;
import com.scorbutics.maven.service.filesystem.target.FileSystemTargetAction;
import com.scorbutics.maven.util.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import lombok.*;

@RequiredArgsConstructor
public class HotDeployer implements FileSystemEventObserver {

	@Builder
	@Value
	static class DeploymentPath {
		Deployment deployment;
		Path       path;
	}

	@FunctionalInterface
	interface FileEventAction {
		void apply(final Path directory, final Path path, FileLockCheckerAndRetryer fileLockCheckerAndRetryer, FileSystemCommonActions fsActions) throws IOException, InterruptedException;
	}

    private       Map<Path, DeploymentPath> pathsToDeploymentMap;
    private final RecursiveDirectoryWatcher directoryWatcher;
    private final FileSystemTargetAction    fileSystemTargetAction;
    private final Path basePath;
    private final Path targetBasePath;
    private final Log             logger;
    private final int redeployDelayMs;
    private TimedTask<Path> redeployTimer;

    public void registerAll(final Collection<Deployment> hotDeployments) {

        pathsToDeploymentMap = hotDeployments.stream()
				.flatMap( Deployment::flatten )
				.filter( Deployment::isEnabled )
                .flatMap(deployment ->
                        directoryWatcher.registerRoots(deployment.getSource(), deployment.getBase(), deployment.computeDirectSubtrees())
							// Check for all exceptions during the watchers setup
							.failAfter(e ->
								new FileWatcherException( "Error during deployment '" + deployment + "' while watching the filesystem: ", e )
							)
							.map(result -> {
								final Path path = basePath.relativize(result);
								return Pair.of(path, DeploymentPath.builder().deployment(deployment).path(path).build());
							})
                )
                .collect(Collectors.toMap(
						Pair::getKey,
						Pair::getValue,
						(deployment1, deployment2) -> {
							throw new FileWatcherException("Multiple deployments configured for the same path: " + deployment1 + " and " + deployment2);
						}));

		this.directoryWatcher.subscribeFunctional(this);
        this.directoryWatcher.startConsumerThread();
        if (redeployDelayMs > 0) {
            this.redeployTimer = new TimedTask<>(Math.min(Math.max(redeployDelayMs, 500), 10000), this::handleThreadedDeployement);
        }
    }

	@Override
    public void onFileCreateModifyEvent(final Path fullPath) {
		doOnEvent( fullPath, (final Path absoluteTargetDirectory, final Path absoluteTargetPath, final FileLockCheckerAndRetryer fileLockCheckerAndRetryer, final FileSystemCommonActions fsActions) -> {
			try {
				fsActions.makeDirectoryOrThrow( absoluteTargetDirectory );
			} catch ( final IOException | FileWatcherException e ) {
				logger.warn( e.getMessage() );
			}
			if ( !this.directoryWatcher.isDirectory( fullPath ) ) {
				if ( this.directoryWatcher.exists( fullPath ) ) {
					// Feed the file
					fileLockCheckerAndRetryer.copyFileWithLockCheck( fullPath, absoluteTargetPath, StandardCopyOption.REPLACE_EXISTING );
				} else {
					logger.debug( "Got create/modify event on '" + fullPath + "' but discarded because the file is not here anymore, despite trying to write inside." );
				}
			}
		});
    }

	@Override
	public void onFileDeleteEvent(final Path fullPath) {
		doOnEvent( fullPath, (final Path absoluteTargetDirectory, final Path absoluteTargetPath, final FileLockCheckerAndRetryer fileLockCheckerAndRetryer, final FileSystemCommonActions fsActions)  -> {
			if ( this.directoryWatcher.isDirectory( fullPath ) ) {
				// Delete the target directory
				fileLockCheckerAndRetryer.deleteIfExists( absoluteTargetPath );
			}
		});
	}

	@Override
	public void onFileOverflowEvent() {
		logger.error( "File system event overflow detected. Some events may have been lost." );
	}

	/*
	 * Common handler for all file events
	 *
	 */
	private void doOnEvent(final Path fullPath, final FileEventAction eventAction) {
		final Optional<Deployment> optionalDeployment = computeDeploymentForPath(fullPath);
		if (!optionalDeployment.isPresent()) {
			logger.warn("Discarding event on '" + basePath.relativize(fullPath) + "' because no deployment matches this path.");
			return;
		}
		final Deployment deployment = optionalDeployment.get();

		final FileSystemCommonActions fsActions = deployment.isUseSourceFilesystemOnly() ? this.directoryWatcher.getSourceReader() : this.fileSystemTargetAction;
		final FileLockCheckerAndRetryer fileLockCheckerAndRetryer = new FileLockCheckerAndRetryer(fsActions, this.directoryWatcher.getSourceReader(), logger);
		final Path absoluteTargetPath = deployment.getTarget().resolve(deployment.getSource().relativize(fullPath));
		final Path absoluteTargetDir = deployment.getTarget().resolve(deployment.getSource().relativize(this.directoryWatcher.isDirectory(fullPath) ? fullPath : fullPath.getParent()));

		try {
			eventAction.apply(absoluteTargetDir, absoluteTargetPath, fileLockCheckerAndRetryer, fsActions);
		} catch (final InterruptedException | FileNotFoundException e) {
			logger.warn("Got event on '" + basePath.relativize(fullPath) + "' but discarded because got an error: " + e.getMessage());
		} catch (final IOException e) {
			logger.warn("IO error while handling path '" + basePath.relativize(fullPath) + "': " + e.getMessage());
		}
	}

	private Optional<Deployment> computeDeploymentForPath(final Path path) {
		logger.debug("Processing event on '" + basePath.relativize(path) + "'");
		final Path relativePath = basePath.relativize(this.directoryWatcher.isDirectory(path) ? path : path.getParent());
		final Deployment deployment = pathsToDeploymentMap.get(relativePath).deployment;
		if (deployment == null) {
			return Optional.empty();
		}

		if (deployment.isRedeployOnChange()) {
			final Path archive = deployment.getEnclosingTargetArchive(targetBasePath);
			if (archive != null) {
				redeployTimer.overrideAndTrigger(archive.getFileName());
			} else {
				logger.warn("Unable to find enclosing archive for redeployment of: " + path);
			}
		}
		return Optional.of(deployment);
	}

    public void waitEvent() throws InterruptedException, IllegalStateException {
        if ( pathsToDeploymentMap == null) {
            throw new IllegalStateException("No directories registered. Please call registerAll() first.");
        }

        // Wait and produce events on this (the main) thread
        this.directoryWatcher.waitProduceFileEvents();
    }

	private void handleThreadedDeployement(final Path archive) {
		try {
			logger.info("Triggering redeployment of archive: " + archive);
			this.fileSystemTargetAction.touchFile(targetBasePath.resolve(archive + ".dodeploy"));
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
    }
}