package com.scorbutics.maven.service.filesystem.watcher;

public interface FileSystemEventFunctionalTechnicalObservable {
	void subscribeTechnicalFileEvent(FileSystemEventObserver observer);

	void unsubscribeTechnicalFileEvent(FileSystemEventObserver observer);

	void subscribeFunctionalFileEvent(FileSystemEventObserver observer);

	void unsubscribeFunctionalFileEvent(FileSystemEventObserver observer);
}