package com.scorbutics.maven.service.filesystem.watcher;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import com.scorbutics.maven.service.filesystem.state.*;

public class FileSystemEventObservableQueue implements FileSystemEventObservable  {
	private final Set<FileSystemEventObserver> observers = new HashSet<>();

	@Override
	public void subscribe( final FileSystemEventObserver observer) {
		observers.add(observer);
	}

	@Override
	public void unsubscribe( final FileSystemEventObserver observer) {
		observers.remove(observer);
	}

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