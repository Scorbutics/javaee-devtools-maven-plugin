package com.scorbutics.maven.service.event.watcher.files.state;

import lombok.*;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.time.Instant;

@Value
public class CoalescedEvent {

    Path               path;
	WatchEvent.Kind<?> kind;
	Instant            timestamp;

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