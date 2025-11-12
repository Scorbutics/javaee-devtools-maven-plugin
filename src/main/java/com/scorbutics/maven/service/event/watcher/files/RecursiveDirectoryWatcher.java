package com.scorbutics.maven.service.event.watcher.files;

import com.scorbutics.maven.exception.FileWatcherException;
import com.scorbutics.maven.service.event.watcher.files.observer.*;
import com.scorbutics.maven.service.event.watcher.files.state.*;
import com.scorbutics.maven.service.filesystem.*;
import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;
import com.scorbutics.maven.util.*;
import com.scorbutics.maven.util.path.*;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.stream.*;

import lombok.*;

// TODO clean up a bit the code
// Be careful this class is very sensitive
public class RecursiveDirectoryWatcher implements FileSystemEventObserver {

	private static final int MAX_PROCESSOR_THREAD_COUNT = 4;

    private final EventWatcher watcher;
	private final FileSystemSourceReader fileSystemSourceReader;
    private final RecursiveDirectoryWalker recursiveDirectoryWalker;
    private final Log logger;
    private final Map<Path, Path> registeredRootPaths = new HashMap<>();
	private final Set<Path> excludedSubtrees = new HashSet<>();
	private final Set<Path> intermediatePaths = new HashSet<>();
	private final int processorThreadCount;

    public RecursiveDirectoryWatcher(final int processorThreadCount, final EventWatcher watcher, final FileSystemSourceReader fileSystemSourceReader, final Log logger) {
        this.watcher = watcher;
		this.processorThreadCount = Math.min( Math.max( processorThreadCount, 1), MAX_PROCESSOR_THREAD_COUNT );
		watcher.subscribeTechnicalFileEvent(this);
        this.fileSystemSourceReader = fileSystemSourceReader;
        this.recursiveDirectoryWalker = new RecursiveDirectoryWalker(this.fileSystemSourceReader);
        this.logger = logger;
    }

	public SafeStream<Path, IOException> registerRoots(final Path sourcePath, final Path basePath, final Set<Path> excludedSubtrees) throws FileWatcherException {
		registeredRootPaths.put(sourcePath, basePath != null ? basePath : sourcePath);
		this.excludedSubtrees.addAll( excludedSubtrees );
		logger.debug( "Registering root path for watching: " + sourcePath + " (base: " + basePath + ")" );

		final List<Path> intermediatePaths = (basePath == null ? Stream.<Path>of() : PathRangeBuilder.range(basePath, sourcePath)).collect( Collectors.toList());


		// Watch every subfolder in the source directory, and all intermediate paths up to the base path
		final SafeStream<Path, IOException> intermediate = SafeStream.<Path, IOException> of(intermediatePaths.stream())
				.tryMap(path -> {
					watcher.register(path,
							StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE,
							StandardWatchEventKinds.ENTRY_MODIFY);
					return path;
				});

		this.intermediatePaths.addAll( intermediatePaths );

		if (basePath != null) {
			final Set<Path> allIntermediates = Stream.concat( intermediatePaths.stream(), Stream.of( sourcePath ) ).collect( Collectors.toSet() );
			this.excludedSubtrees.addAll( this.recursiveDirectoryWalker.readRecursive( basePath, allIntermediates.size(), null)
					.filter( path -> !allIntermediates.contains( path ) )
					.collect( Collectors.toSet() )
			);
		}
		logger.debug( "Excluding subtrees from watching: " + this.excludedSubtrees );

		final List<Path> subdirectories = new ArrayList<>();
		this.recursiveDirectoryWalker.walk(logger, sourcePath, Integer.MAX_VALUE, this.excludedSubtrees, new SimpleFileVisitor<Path>() {
			@Override
			public @NonNull FileVisitResult preVisitDirectory(final @NonNull Path dir, final @NonNull BasicFileAttributes attrs) {
				try {
					if (fileSystemSourceReader.exists(dir)) {
						subdirectories.add(dir);
						watcher.register( dir,
								StandardWatchEventKinds.ENTRY_CREATE,
								StandardWatchEventKinds.ENTRY_DELETE,
								StandardWatchEventKinds.ENTRY_MODIFY );
					}
				} catch (final IOException e) {
					logger.warn("Error while registering root directory for watching: " + e.getMessage() + " " + e.getClass().getSimpleName());
					return FileVisitResult.CONTINUE;
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return SafeStream.concat( intermediate, SafeStream.of( subdirectories.stream() ) );
	}

    private SafeStream<Path, IOException> registerRecursive(final Path sourcePath) throws FileWatcherException {

		if ( excludedSubtrees.contains( sourcePath ) ) {
			logger.debug("Discarding directory " + sourcePath);
			return SafeStream.empty();
		}

		if (this.intermediatePaths.contains( sourcePath )) {
			logger.debug( "Registering single intermediate path for watching: " + sourcePath );
			try {
				watcher.register( sourcePath,
						StandardWatchEventKinds.ENTRY_CREATE,
						StandardWatchEventKinds.ENTRY_DELETE,
						StandardWatchEventKinds.ENTRY_MODIFY );
				return SafeStream.of( Stream.of( sourcePath ) );
			} catch ( final IOException e ) {
				logger.warn("Error while registering intermediate directory for watching: " + e.getMessage() + " " + e.getClass().getSimpleName());
				return SafeStream.empty();
			}
		}

		final List<Path> subdirectories = new ArrayList<>();
		this.recursiveDirectoryWalker.walk(logger, sourcePath, Integer.MAX_VALUE, excludedSubtrees, new SimpleFileVisitor<Path>() {
			@Override
			public @NonNull FileVisitResult preVisitDirectory(final @NonNull Path dir, final @NonNull BasicFileAttributes attrs) {
				try {
					if (fileSystemSourceReader.exists(dir)) {
						subdirectories.add(dir);
						logger.debug("Registering directory " + dir);
						watcher.register( dir,
								StandardWatchEventKinds.ENTRY_CREATE,
								StandardWatchEventKinds.ENTRY_DELETE,
								StandardWatchEventKinds.ENTRY_MODIFY );
					}
				} catch (final IOException e) {
					logger.warn("Error while registering directory for watching: " + e.getMessage() + " " + e.getClass().getSimpleName());
					return FileVisitResult.CONTINUE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public @NonNull FileVisitResult visitFile(final @NonNull Path file, final @NonNull BasicFileAttributes attrs ) {
				// Notify about potential skipped files
				watcher.offerEvent( PathEvent.builder()
						.path(file)
						.kind(StandardWatchEventKinds.ENTRY_CREATE).build() );

				return FileVisitResult.CONTINUE;
			}
		});
		return SafeStream.of( subdirectories.stream() );
    }

    public void waitProduceFileEvents() throws InterruptedException {
		watcher.waitProduceFileEvents( registeredRootPaths.keySet() );
	}

    public boolean isDirectory(final Path path) {
        return this.fileSystemSourceReader.isDirectory(path);
    }

    public FileSystemSourceReader getSourceReader() {
        return this.fileSystemSourceReader;
    }

    public boolean exists(final Path path) {
        return this.fileSystemSourceReader.exists(path);
    }

    public void startConsumerThread() {
        this.watcher.startConsuming(processorThreadCount);
    }

	@Override
	public void onFileCreateEvent(final Path fullPath) {
		if (!this.fileSystemSourceReader.isDirectory(fullPath)) {
			// File is not a directory, ignore
			return;
		}

		final boolean success = registerRecursive(fullPath)
				.logFailures(e -> logger.warn("Error while registering new directory for watching: " + e.getMessage()))
				.allSuccess();
		if (!success) {
			logger.warn("Giving up registering new directory for watching: " + fullPath.toString());
		}
	}

    @Override
    public void onFileModifyEvent(final Path fullPath) {
        onFileCreateEvent(fullPath);
    }

	public void subscribeFunctional(final FileSystemEventObserver observer) {
		watcher.subscribeFunctionalFileEvent( observer );
	}

	public void subscribeTechnical(final FileSystemEventObserver observer) {
		watcher.subscribeTechnicalFileEvent( observer );
	}
}