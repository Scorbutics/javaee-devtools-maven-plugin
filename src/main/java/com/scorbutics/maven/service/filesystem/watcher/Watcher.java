package com.scorbutics.maven.service.filesystem.watcher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Collection;
import java.util.Optional;

public interface Watcher {
    void register(Path path, WatchEvent.Kind<Path>... entries) throws IOException;

    Optional<Path> waitProduceFileEvents(final Collection<Path> includeOnly) throws InterruptedException;

    void startConsuming(final int processorThreadCount);

	void offerEvent( PathEvent build );
}