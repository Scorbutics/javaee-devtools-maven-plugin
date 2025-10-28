package com.scorbutics.maven.service.filesystem.state;

import com.scorbutics.maven.service.filesystem.watcher.PathEvent;

import lombok.*;

import org.apache.maven.plugin.logging.Log;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.*;

@RequiredArgsConstructor
public class FileEventCoalescer {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	// Queue maintains insertion order
	private final ConcurrentLinkedQueue<Path> eventOrder = new ConcurrentLinkedQueue<>();
	// Map for fast lookup and merging
	private final Map<Path, CoalescedEvent> pendingEvents = new ConcurrentHashMap<>();
	// Track what's in the queue to avoid duplicates
	private final Set<Path> inQueue = ConcurrentHashMap.newKeySet();

	private final Duration debounceWindow;
    private final Consumer<Stream<CoalescedEvent>> eventProcessor;
    private final Log logger;

	private       ScheduledFuture<?> globalFlush;
	private final Object             flushLock = new Object();

    public void submitEvent(final PathEvent event) {
        final Path normalizedPath = event.path.normalize();

		// Check if this is a new path
		final boolean isNew = inQueue.add(normalizedPath);

		// Merge or create event
		pendingEvents.compute(normalizedPath, (k, existing) -> {
			if (existing != null) {
				existing.merge(event.kind);
				return existing;
			}
			return new CoalescedEvent(normalizedPath, event.kind);
		});

		// Add to queue only if new
		if (isNew) {
			eventOrder.offer(normalizedPath);
		}

		// Reset global debounce timer
		synchronized (flushLock) {
			if (globalFlush != null && !globalFlush.isDone()) {
				globalFlush.cancel(false);
			}

			globalFlush = scheduler.schedule(this::flushAllEvents,
					debounceWindow.toMillis(), TimeUnit.MILLISECONDS);
		}
	}

    private void flushAllEvents() {
		synchronized (flushLock) {
			eventOrder.stream()
					.map(pendingEvents::remove)
					.filter(Objects::nonNull)
					.filter(event -> !event.isNoOp())
					.forEach(event -> {
						inQueue.remove(event.path);
						final List<CoalescedEvent> grouped = groupRelatedEvents(event);
						eventProcessor.accept( grouped.stream() );
					});
			eventOrder.clear();
		}
    }

	private List<CoalescedEvent> groupRelatedEvents(final CoalescedEvent triggerEvent) {
		final List<CoalescedEvent> related = new ArrayList<>();
		related.add(triggerEvent);

		Path current = triggerEvent.path.getParent();
		while (current != null) {
			// Check for parent directory deletions that subsume this event
			final CoalescedEvent parentEvent = pendingEvents.get(current);
			if (parentEvent != null && parentEvent.isDeletion()) {
				logger.debug("Parent deletion event found for path: " + current + ", subsuming event for path: " + triggerEvent.path);
				// Parent deletion subsumes child events
				pendingEvents.remove(triggerEvent.path);
				related.clear();
				related.add(parentEvent);
				break;
			}

			current = current.getParent();
		}

		// Check for child events that can be subsumed
		if (triggerEvent.isDeletion()) {
			final Iterator<Map.Entry<Path, CoalescedEvent>> iter = pendingEvents.entrySet().iterator();
			while (iter.hasNext()) {
				final Map.Entry<Path, CoalescedEvent> entry = iter.next();
				if (entry.getKey().startsWith(triggerEvent.path) &&
						!entry.getKey().equals(triggerEvent.path)) {
					// This is a child event that's subsumed by parent deletion
					logger.debug("Child event for path: " + entry.getKey() + " subsumed by parent deletion for path: " + triggerEvent.path);
					iter.remove();
				}
			}
		}

		return related;
	}

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (final InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}