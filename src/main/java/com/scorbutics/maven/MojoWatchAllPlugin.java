package com.scorbutics.maven;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.scorbutics.maven.model.*;

import org.apache.maven.plugins.annotations.*;

import com.scorbutics.maven.service.*;
import com.scorbutics.maven.service.event.observer.*;
import com.scorbutics.maven.service.event.watcher.compilation.*;
import com.scorbutics.maven.service.event.watcher.files.*;
import com.scorbutics.maven.service.filesystem.*;
import com.scorbutics.maven.service.filesystem.target.*;
import com.scorbutics.maven.service.packaging.*;
import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;
import com.scorbutics.maven.util.*;

@Mojo(name = "watch-all", threadSafe = true, aggregator = true)
public class MojoWatchAllPlugin
		extends BaseMojoDeploymentPlugin {

	@Parameter(property = "watcher")
	private WatcherConfiguration watcher;

	@Override
	protected Stream<ProjectComputer> getProjectComputers( final FileSystemSourceReader fileSystemSourceReader ) {
		return Stream.of(
				ProjectPluginsAnalyzer.builder().fileSystemSourceReader( fileSystemSourceReader ).logger( getLog() ).build(),
				ProjectMavenReactorAnalyzer.builder().fileSystemSourceReader( fileSystemSourceReader).logger(getLog() ).build()
		);
	}

	@Override
	protected DeploymentType getDeploymentType() {
		return DeploymentType.HOT_DEPLOYMENT;
	}


	@Override
	protected Predicate<Deployment> filterDeployment() {
		// exclude root deployments while watching
		return deployment -> deployment.getDepth() > 0 || !deployment.isComputed();
	}

	@Override
	protected void actOnDeployments( final Path basePath, final Path target, final FileSystemSourceReader fileSystemSourceReader, final FileSystemTargetAction fileSystemTargetAction, final Collection<Deployment> allDeployments ) {
		final EventWatcher eventWatcher;
		try {
			eventWatcher = new LocalFileSystemWatcher( watcher.getDebounce(), getLog());
		} catch ( final IOException e ) {
			throw new RuntimeException( e );
		}

		final WatcherEventLogger eventLogger = WatcherEventLogger.builder()
				.logger( getLog() )
				.sourceDir( basePath )
				.showProgress( watcher.isShowProgress() )
				.verbose( watcher.isVerbose() )
				.build();
		final RecursiveDirectoryWatcher directoryWatcher = new RecursiveDirectoryWatcher(watcher.getThreads(), eventWatcher, fileSystemSourceReader, getLog());
		directoryWatcher.subscribeFunctional(eventLogger);

		final CompilationEventWatcher compilationEventWatcher = new CompilationEventWatcher(getLog(), fileSystemSourceReader, allDeployments);
		directoryWatcher.subscribeTechnical(compilationEventWatcher);
		final FileLockCheckerAndRetryer fileLockCheckerAndRetryer = new FileLockCheckerAndRetryer(fileSystemTargetAction, fileSystemSourceReader, getLog());
		final MavenMetaInfIntegration mavenMetaInfIntegration = new MavenMetaInfIntegration(fileSystemSourceReader, fileSystemTargetAction, fileLockCheckerAndRetryer, allDeployments, getLog());
		compilationEventWatcher.subscribe( mavenMetaInfIntegration );

		final HotDeployer hotDeployer = new HotDeployer(directoryWatcher, fileSystemTargetAction, basePath, target, getLog());
		hotDeployer.registerAll(allDeployments);

		getLog().info("Watching...");
		for (;;) {
			try {
				hotDeployer.waitEvent();
			} catch (final InterruptedException e) {
				getLog().info("Hot Deployment interrupted: " + e.getMessage());
				break;
			}
		}
	}
}