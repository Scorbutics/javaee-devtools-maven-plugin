package com.scorbutics.maven.service.event.watcher.compilation;

public interface CompilationEventObservable {
	void notifyCleanEvent(CompilationEvent event);
	void notifyBuildFinishedEvent(CompilationEvent event);
}