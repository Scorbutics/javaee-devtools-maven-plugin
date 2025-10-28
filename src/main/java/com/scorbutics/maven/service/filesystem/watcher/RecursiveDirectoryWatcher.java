package com.scorbutics.maven.service.filesystem.watcher;

import com.scorbutics.maven.*;
import com.scorbutics.maven.service.filesystem.*;
import com.scorbutics.maven.exception.FileWatcherException;
import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;
import com.scorbutics.maven.util.*;
import com.scorbutics.maven.util.path.*;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.stream.*;

public class RecursiveDirectoryWatcher implements FileSystemEventObserver {

	// TODO could be fine tuned? or exposed to configuration?
	private static final int PROCESSOR_THREAD_COUNT = 4;

	private final FileSystemEventObservableQueue queue = new FileSystemEventObservableQueue();
    private final EventWatcher watcher;
	private final FileSystemSourceReader fileSystemSourceReader;
    private final RecursiveDirectoryWalker recursiveDirectoryWalker;
    private final Log logger;
    private final Map<Path, Path> registeredRootPaths = new HashMap<>();

    public RecursiveDirectoryWatcher(final EventWatcher watcher, final FileSystemSourceReader fileSystemSourceReader, final Log logger) {
        this.watcher = watcher;
		watcher.subscribeTechnicalFileEvent(this);
        this.fileSystemSourceReader = fileSystemSourceReader;
        this.recursiveDirectoryWalker = new RecursiveDirectoryWalker(this.fileSystemSourceReader);
        this.logger = logger;
    }

	// TODO ugly method, refactor
    public SafeStream<Path, IOException> registerRecursive(final Path sourcePath, final Path basePath, final Set<Path> excludedSubtrees, final boolean isRoot) throws FileWatcherException {
		if (isRoot) {
        	final Path realRootPath = basePath != null ? basePath : sourcePath;
            registeredRootPaths.put(sourcePath, realRootPath);
        }

		final Stream<Path> intermediatePaths = basePath == null ? Stream.of() : PathRangeBuilder.range(basePath, sourcePath);

		// Watch every subfolder in the source directory, and all intermediate paths up to the base path
		final SafeStream<Path, IOException> intermediate = SafeStream.<Path, IOException> of(intermediatePaths)
				.distinct()
				.tryMap(path -> {
					watcher.register(path,
							StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE,
							StandardWatchEventKinds.ENTRY_MODIFY);
					return path;
				});

		final List<Path> subdirectories = new ArrayList<>();

		this.recursiveDirectoryWalker.walk(logger, sourcePath, Integer.MAX_VALUE, excludedSubtrees, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
				try {
					subdirectories.add(dir);
					if (fileSystemSourceReader.exists(dir)) {
						watcher.register( dir,
								StandardWatchEventKinds.ENTRY_CREATE,
								StandardWatchEventKinds.ENTRY_DELETE,
								StandardWatchEventKinds.ENTRY_MODIFY );
					}
				} catch (final IOException e) {
					logger.warn("Error while registering directory for watching: " + e.getMessage(), e);
					return FileVisitResult.CONTINUE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs ) {
				// Notify about potential skipped files
				if (!isRoot) {
					//logger.info( "Notifying about existing file: " + file.toString() );
					watcher.offerEvent( PathEvent.builder()
							.path(file)
							.kind(StandardWatchEventKinds.ENTRY_CREATE).build() );
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return SafeStream.concat( intermediate, SafeStream.of( subdirectories.stream() ) );
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
        this.watcher.startConsuming(PROCESSOR_THREAD_COUNT);
    }

	@Override
	public void onFileCreateModifyEvent(final Path fullPath) {
		if (!this.fileSystemSourceReader.isDirectory(fullPath)) {
			// File is not a directory, ignore
			return;
		}

		final boolean success = registerRecursive( fullPath, null, null, false)
				.logFailures(e -> logger.warn("Error while registering new directory for watching: " + e.getMessage()))
				.allSuccess();
		if (!success) {
			logger.warn("Giving up registering new directory for watching: " + fullPath.toString());
		}
	}

	public void subscribe(final FileSystemEventObserver observer) {
		watcher.subscribeFunctionalFileEvent( observer );
	}
}