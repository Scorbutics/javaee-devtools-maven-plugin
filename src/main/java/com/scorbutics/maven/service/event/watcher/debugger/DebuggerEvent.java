package com.scorbutics.maven.service.event.watcher.debugger;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class DebuggerEvent {
    int debugPort;
    boolean attached;
    long timestamp;
}

