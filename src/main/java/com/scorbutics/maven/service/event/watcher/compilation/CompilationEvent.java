package com.scorbutics.maven.service.event.watcher.compilation;

import com.scorbutics.maven.model.*;

import lombok.*;

@Builder
@Value
public class CompilationEvent {
	Deployment deployment;
}