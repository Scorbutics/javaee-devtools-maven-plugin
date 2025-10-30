package com.scorbutics.maven.service.event.watcher.files.observer;

import java.nio.file.*;

import com.scorbutics.maven.service.event.observer.*;

public class FileSystemEventObservableQueue extends ObservableQueue<FileSystemEventObserver>
		implements FileSystemEventObservable  {

	@Override
	public void notifyCreateModifyEvent(final Path fullPath) {
		observers.forEach(observer -> observer.onFileCreateModifyEvent(fullPath));
	}

	@Override
	public void notifyDeleteEvent(final Path fullPath) {
		observers.forEach(observer -> observer.onFileDeleteEvent(fullPath));
	}

	@Override
	public void notifyOverflowEvent() {
		observers.forEach( FileSystemEventObserver::onFileOverflowEvent );
	}

}