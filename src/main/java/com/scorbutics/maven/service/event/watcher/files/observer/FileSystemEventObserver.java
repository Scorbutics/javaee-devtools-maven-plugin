package com.scorbutics.maven.service.event.watcher.files.observer;

import java.nio.file.*;


public interface FileSystemEventObserver {
	default void onFileCreateEvent(final Path fullPath) {
		// Default no-op implementation
	}

	default void onFileDeleteEvent(final Path fullPath) {
		// Default no-op implementation
	}

	default void onFileOverflowEvent() {
		// Default no-op implementation
	}

    default void onFileModifyEvent(final Path fullPath) {
        // Default no-op implementation
    }
}