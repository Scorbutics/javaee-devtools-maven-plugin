package com.scorbutics.maven.service.event.watcher.files.observer;

import java.nio.file.*;


public interface FileSystemEventObservable {
	void notifyCreateEvent(Path fullPath);
    void notifyModifyEvent(Path fullPath);
	void notifyDeleteEvent(Path fullPath);
	void notifyOverflowEvent();
}