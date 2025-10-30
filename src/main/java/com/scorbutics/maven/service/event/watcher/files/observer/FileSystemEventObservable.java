package com.scorbutics.maven.service.event.watcher.files.observer;

import java.nio.file.*;


public interface FileSystemEventObservable {
	void notifyCreateModifyEvent(Path fullPath);
	void notifyDeleteEvent(Path fullPath);
	void notifyOverflowEvent();
}