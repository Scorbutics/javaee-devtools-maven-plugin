package com.scorbutics.maven.service.event.watcher.compilation;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

import org.apache.maven.plugin.logging.*;

import com.scorbutics.maven.model.*;
import com.scorbutics.maven.service.event.observer.*;
import com.scorbutics.maven.service.event.watcher.files.observer.*;
import com.scorbutics.maven.service.filesystem.source.*;

import lombok.*;

public class CompilationEventWatcher extends ObservableQueue<CompilationEventObserver>
		implements CompilationEventObservable, FileSystemEventObserver, AutoCloseable {

	private static final int QUEUE_CAPACITY = 1000;
	private static final long DEBOUNCE_WINDOW_MS = 500; // 500ms debounce window

	private final FileSystemSourceReader fileSystemSourceReader;
	private final Map<Path, Deployment> targetArchiveBuiltToDeployment;

	// Producer/Consumer components
	private final BlockingQueue<FileSystemEvent> eventQueue;
	private final ExecutorService consumerExecutor;
	private final ScheduledExecutorService debounceExecutor;

	// Debouncing state
	private final Map<Path, ScheduledFuture<?>> pendingEvents;
	private final Object debounceLock = new Object();

	private volatile boolean running = true;
	private final Log logger;

	public CompilationEventWatcher( final Log logger, final FileSystemSourceReader fileSystemSourceReader,
			final Collection<Deployment> deployments) {
		this.logger = logger;
		this.fileSystemSourceReader = fileSystemSourceReader;
		this.targetArchiveBuiltToDeployment = deployments.stream()
				.flatMap(Deployment::flatten)
				.filter(Deployment::isEnabled)
				.filter(deployment -> deployment.getArchive() != null)
				.collect(Collectors.toMap(Deployment::getArchive, Function.identity()));

		// Initialize producer/consumer infrastructure
		this.eventQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
		this.consumerExecutor = Executors.newSingleThreadExecutor(r -> {
			final Thread thread = new Thread(r, "CompilationEventConsumer");
			thread.setDaemon(true);
			return thread;
		});
		this.debounceExecutor = Executors.newScheduledThreadPool(1, r -> {
			final Thread thread = new Thread(r, "CompilationEventDebouncer");
			thread.setDaemon(true);
			return thread;
		});
		this.pendingEvents = new ConcurrentHashMap<>();

		// Start the consumer
		startConsumer();
	}

	/**
	 * Starts the consumer thread that processes events from the queue
	 */
	private void startConsumer() {
		consumerExecutor.submit(() -> {
			while (running || !eventQueue.isEmpty()) {
				try {
					final FileSystemEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
					if (event != null) {
						processEvent(event);
					}
				} catch ( final InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				} catch ( final Exception e) {
					logger.warn( "Error processing file system event", e);
				}
			}
		});
	}

	/**
	 * Producer method - adds events to queue without blocking
	 */
	@Override
	public void onFileCreateModifyEvent(final Path fullPath) {
		if (!running) {
			logger.warn("Watcher is stopped, ignoring event for: " + fullPath);
			return;
		}

		try {
			final boolean added = eventQueue.offer(
					new FileSystemEvent(fullPath, EventType.CREATE_OR_MODIFY),
					100,
					TimeUnit.MILLISECONDS
			);

			if (!added) {
				logger.warn("Event queue is full, dropped event for: " + fullPath);
			}
		} catch ( final InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Interrupted while adding event to queue", e);
		}
	}

	/**
	 * Producer method - adds events to queue without blocking
	 */
	@Override
	public void onFileDeleteEvent(final Path fullPath) {
		if (!running) {
			logger.warn("Watcher is stopped, ignoring event for: " + fullPath);
			return;
		}

		try {
			final boolean added = eventQueue.offer(
					new FileSystemEvent(fullPath, EventType.DELETE),
					100,
					TimeUnit.MILLISECONDS
			);

			if (!added) {
				logger.warn("Event queue is full, dropped event for: " + fullPath);
			}
		} catch ( final InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Interrupted while adding event to queue", e);
		}
	}

	/**
	 * Consumer method - processes events with debouncing
	 */
	private void processEvent(final FileSystemEvent event) {
		synchronized (debounceLock) {
			// Cancel any pending event for this path
			final ScheduledFuture<?> existingFuture = pendingEvents.get(event.getPath());
			if (existingFuture != null && !existingFuture.isDone()) {
				existingFuture.cancel(false);
			}

			// Schedule the new event with debounce window
			final ScheduledFuture<?> future = debounceExecutor.schedule(
					() -> executeEvent(event),
					DEBOUNCE_WINDOW_MS,
					TimeUnit.MILLISECONDS
			);

			pendingEvents.put(event.getPath(), future);
		}
	}

	/**
	 * Executes the actual event processing after debounce window
	 */
	private void executeEvent(final FileSystemEvent event) {
		try {
			synchronized (debounceLock) {
				pendingEvents.remove(event.getPath());
			}

			if (!targetArchiveBuiltToDeployment.containsKey(event.getPath())) {
				return;
			}

			final Deployment deployment = targetArchiveBuiltToDeployment.get(event.getPath());
			final CompilationEvent compilationEvent = CompilationEvent.builder()
					.deployment(deployment)
					.build();

			switch (event.getType()) {
				case CREATE_OR_MODIFY:
					if (fileSystemSourceReader.exists(event.getPath())) {
						notifyBuildFinishedEvent(compilationEvent);
					}
					break;
				case DELETE:
					notifyCleanEvent(compilationEvent);
					break;
			}
		} catch ( final Exception e) {
			logger.error("Error executing event for path: " + event.getPath() + " - " + e.getMessage());
		}
	}

	@Override
	public void notifyCleanEvent(final CompilationEvent event) {
		observers.forEach(observer -> {
			try {
				observer.onCleanEvent(event);
			} catch ( final Exception e) {
				logger.error("Error notifying observer about clean event " + e.getMessage() );
			}
		});
	}

	@Override
	public void notifyBuildFinishedEvent(final CompilationEvent event) {
		observers.forEach(observer -> {
			try {
				observer.onBuildFinishedEvent(event);
			} catch ( final Exception e) {
				logger.error("Error notifying observer about build finished event " + e.getMessage());
			}
		});
	}

	/**
	 * Gracefully shuts down the watcher
	 */
	@Override
	public void close() {
		running = false;

		// Shutdown executors gracefully
		debounceExecutor.shutdown();
		consumerExecutor.shutdown();

		try {
			if (!debounceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				debounceExecutor.shutdownNow();
			}
			if (!consumerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				consumerExecutor.shutdownNow();
			}
		} catch ( final InterruptedException e) {
			debounceExecutor.shutdownNow();
			consumerExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}

	}

	/**
	 * Internal event representation
	 */
	@Value
	private static class FileSystemEvent {
		Path path;
		EventType type;
	}

	/**
	 * Event type enumeration
	 */
	private enum EventType {
		CREATE_OR_MODIFY,
		DELETE
	}
}