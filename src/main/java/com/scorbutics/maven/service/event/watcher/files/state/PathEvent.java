package com.scorbutics.maven.service.event.watcher.files.state;

import lombok.Builder;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

@Builder
public class PathEvent {
    public Path path;
    public WatchEvent.Kind<?> kind;
}