package com.scorbutics.maven.service.filesystem.watcher;

import java.nio.file.*;
import java.util.stream.*;

import com.scorbutics.maven.service.filesystem.state.*;

public interface FileSystemEventObserver {
	default void onFileCreateModifyEvent(final Path fullPath) {
		// Default no-op implementation
	}

	default void onFileDeleteEvent(final Path fullPath) {
		// Default no-op implementation
	}

	default void onFileOverflowEvent() {
		// Default no-op implementation
	}
}