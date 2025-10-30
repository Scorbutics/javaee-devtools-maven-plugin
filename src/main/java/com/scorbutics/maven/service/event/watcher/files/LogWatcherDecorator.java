package com.scorbutics.maven.service.event.watcher.files;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Collection;
import java.util.Optional;

import com.scorbutics.maven.service.event.watcher.files.observer.*;
import com.scorbutics.maven.service.event.watcher.files.state.*;

public class LogWatcherDecorator implements EventWatcher {
    private final EventWatcher decoratedWatcher;
    private final Log logger;

    public LogWatcherDecorator(final EventWatcher decoratedWatcher, final Log logger) {
        this.decoratedWatcher = decoratedWatcher;
        this.logger = logger;
    }

    @Override
    public void register(final Path path, final WatchEvent.Kind<Path>... entries) throws IOException {
        logger.debug("Registering path: " + path);
        decoratedWatcher.register(path, entries);
    }

    @Override
    public Optional<Path> waitProduceFileEvents(final Collection<Path> includeOnly) throws InterruptedException {
        return decoratedWatcher.waitProduceFileEvents(includeOnly);
    }

    @Override
    public void startConsuming(final int processorThreadCount) {
        this.logger.info("Starting watcher thread with " + processorThreadCount + " processor threads.");
        decoratedWatcher.startConsuming(processorThreadCount);
    }

	@Override
	public void offerEvent( final PathEvent build ) {
		decoratedWatcher.offerEvent( build );
	}

	@Override
	public void subscribeTechnicalFileEvent( final FileSystemEventObserver observer ) {
		decoratedWatcher.subscribeTechnicalFileEvent(observer);
	}

	@Override
	public void unsubscribeTechnicalFileEvent( final FileSystemEventObserver observer ) {
		decoratedWatcher.unsubscribeTechnicalFileEvent(observer);
	}

	@Override
	public void subscribeFunctionalFileEvent( final FileSystemEventObserver observer ) {
		decoratedWatcher.subscribeFunctionalFileEvent(observer);
	}

	@Override
	public void unsubscribeFunctionalFileEvent( final FileSystemEventObserver observer ) {
		decoratedWatcher.unsubscribeFunctionalFileEvent(observer);
	}
}