package com.scorbutics.maven.service.filesystem.state;

import lombok.Getter;
import org.apache.maven.plugin.logging.Log;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CoalescedEvent {
	@Getter
    final Path               path;
	@Getter
	final WatchEvent.Kind<?> kind; // Immutable!
	final Instant            timestamp;

    CoalescedEvent(final Path path, final WatchEvent.Kind<?> kind) {
        this.path = path;
        this.kind = kind;
        this.timestamp = Instant.now();
    }

    /**
     * Returns a NEW event representing the merged state.
     * This object remains unchanged (immutable).
     */
    CoalescedEvent merge(final WatchEvent.Kind<?> newKind) {
        final WatchEvent.Kind<?> resultKind = computeMergedKind(this.kind, newKind);
        return new CoalescedEvent(this.path, resultKind);
    }

    private static WatchEvent.Kind<?> computeMergedKind(
            final WatchEvent.Kind<?> currentKind,
            final WatchEvent.Kind<?> newKind) {

        // CREATE + DELETE = nothing
        // CREATE + MODIFY = CREATE
		// DELETE + CREATE = MODIFY (deleted then recreated = modification)
		// DELETE + MODIFY = DELETE (should not happen, but treat as DELETE)
		// ANY1 + ANY2 = ANY2 (otherwise)
        if (currentKind == StandardWatchEventKinds.ENTRY_CREATE) {
            if (newKind == StandardWatchEventKinds.ENTRY_DELETE) {
                return null;
            }
            return StandardWatchEventKinds.ENTRY_CREATE;
        } else if (currentKind == StandardWatchEventKinds.ENTRY_DELETE) {
			if (newKind == StandardWatchEventKinds.ENTRY_CREATE) {
				return StandardWatchEventKinds.ENTRY_MODIFY;
			}
			return StandardWatchEventKinds.ENTRY_DELETE;
		}
        
        return newKind;
    }

    boolean isDeletion() {
        return kind == StandardWatchEventKinds.ENTRY_DELETE;
    }

	public boolean isNoOp() {
        return this.kind == null;
    }
}