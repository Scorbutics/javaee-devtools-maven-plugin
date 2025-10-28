package com.scorbutics.maven.service.filesystem.watcher;

import java.nio.file.*;
import java.util.stream.*;

import com.scorbutics.maven.service.filesystem.state.*;

public interface FileSystemEventObservable {
	void subscribe(FileSystemEventObserver observer);
	void unsubscribe(FileSystemEventObserver observer);
	void notifyCreateModifyEvent(Path fullPath);
	void notifyDeleteEvent(Path fullPath);
	void notifyOverflowEvent();
}