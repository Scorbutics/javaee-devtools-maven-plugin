package com.scorbutics.maven.service.event.watcher.files.observer;

public interface FileSystemEventFunctionalTechnicalObservable {
	void subscribeTechnicalFileEvent(FileSystemEventObserver observer);

	void unsubscribeTechnicalFileEvent(FileSystemEventObserver observer);

	void subscribeFunctionalFileEvent(FileSystemEventObserver observer);

	void unsubscribeFunctionalFileEvent(FileSystemEventObserver observer);
}