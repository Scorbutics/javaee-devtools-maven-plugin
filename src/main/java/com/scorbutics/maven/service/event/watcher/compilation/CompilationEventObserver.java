package com.scorbutics.maven.service.event.watcher.compilation;

public interface CompilationEventObserver {
	void onCleanEvent(CompilationEvent event);
	void onBuildFinishedEvent(CompilationEvent event);
}