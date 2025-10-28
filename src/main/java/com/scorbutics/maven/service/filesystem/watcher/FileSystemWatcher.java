package com.scorbutics.maven.service.filesystem.watcher;

import com.scorbutics.maven.service.filesystem.state.*;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.*;

import lombok.*;

public class FileSystemWatcher implements EventWatcher  {
    private final WatchService watcher;

	@Getter
	private final FileSystemEventObservableQueue technicalConsumerEventQueue = new FileSystemEventObservableQueue();
	@Getter
	private final FileSystemEventObservableQueue functionalConsumerEventQueue = new FileSystemEventObservableQueue();
    private final BlockingQueue<PathEvent>       producerEventQueue;
    private boolean                              started;
    private final Log logger;

    public FileSystemWatcher(final int queueCapacity, final WatchService watcher, final Log logger) {
        this.watcher = watcher;
        // Bounded queue with your chosen capacity
        this.producerEventQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.logger = logger;
    }

    @Override
    public void startConsuming(final int processorThreadCount) {
        if (this.started) {
            throw new IllegalStateException("Processing already started");
        }
        this.started = true;

        final ExecutorService processorThreads = Executors.newFixedThreadPool(processorThreadCount);
        final ExecutorService watcherThread = Executors.newSingleThreadExecutor();
        // Debounce window: adjust based on your typical "bunch" duration
        final FileEventCoalescer coalescer = new FileEventCoalescer(
                Duration.ofMillis(400),
                events -> processorThreads.submit(() -> {
					events.forEach(event ->
						dispatchEventToQueue(functionalConsumerEventQueue, event.getPath(), event.getKind())
					);
				} ),
                logger
        );

        watcherThread.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    coalescer.submitEvent( producerEventQueue.take());
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

	@Override
	public void subscribeTechnicalFileEvent( final FileSystemEventObserver observer ) {
		this.technicalConsumerEventQueue.subscribe(observer);
	}

	@Override
	public void unsubscribeTechnicalFileEvent( final FileSystemEventObserver observer ) {
		this.technicalConsumerEventQueue.unsubscribe(observer);
	}

	@Override
	public void subscribeFunctionalFileEvent( final FileSystemEventObserver observer ) {
		this.functionalConsumerEventQueue.subscribe(observer);
	}

	@Override
	public void unsubscribeFunctionalFileEvent( final FileSystemEventObserver observer ) {
		this.functionalConsumerEventQueue.unsubscribe(observer);
	}

	@Override
    public void register(final Path path, final WatchEvent.Kind<Path>... entries) throws IOException {
        path.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
    }

    @Override
    public Optional<Path> waitProduceFileEvents(final Collection<Path> includeOnly) throws InterruptedException {
        final WatchKey key = watcher.take();
        if (key == null) {
            return Optional.empty();
        }
        final Path dir = (Path) key.watchable();
        key.pollEvents().forEach(event -> {
			final Path relativePath = (Path) event.context();
			if (relativePath == null) {
				return;
			}

			// The technical consumption is done sequentially in the main thread before any other action is done.
			// This is important to take direct action on some events, like registering a newly created folder.
            final Path fullPath = dir.resolve(relativePath);
			dispatchEventToQueue(technicalConsumerEventQueue, fullPath, event.kind());

            // Exclude events outside the includeOnly path if specified
            if (includeOnly != null && includeOnly.stream().noneMatch( fullPath::startsWith ) ) {
				logger.debug("Skipping event for path outside monitored directories: " + fullPath);
                return;
            }
            this.offerEvent(PathEvent.builder()
                .path(fullPath)
                .kind(event.kind()).build());
        });
        // Reset the key -- this step is critical if you want to receive further watch events.
        return key.reset() ? Optional.empty() : Optional.of(dir);
    }

	private void dispatchEventToQueue(final FileSystemEventObservableQueue eventQueue, final Path fullPath, final WatchEvent.Kind<?> kind) {
		if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
			eventQueue.notifyCreateModifyEvent(fullPath);
		} else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
			eventQueue.notifyDeleteEvent(fullPath);
		} else if (kind == StandardWatchEventKinds.OVERFLOW) {
			eventQueue.notifyOverflowEvent();
		}
	}

	@Override
	public void offerEvent(final PathEvent event) {
        // Non-blocking offer with timeout
        try {
			if (!producerEventQueue.offer( event, 100, TimeUnit.MILLISECONDS)) {
				logger.warn("Event queue is full, dropping event: " + event);
			}
		} catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
		}
    }

}