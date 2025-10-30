package com.scorbutics.maven.service.event.watcher.files;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Collection;
import java.util.Optional;

import com.scorbutics.maven.service.event.watcher.files.state.*;

public interface Watcher {
    void register(Path path, WatchEvent.Kind<Path>... entries) throws IOException;

    Optional<Path> waitProduceFileEvents(final Collection<Path> includeOnly) throws InterruptedException;

    void startConsuming(final int processorThreadCount);

	void offerEvent( PathEvent build );
}