package com.scorbutics.maven.service.event.watcher.debugger;

public interface DebuggerEventObserver {
	void onDebuggerAttached(DebuggerEvent event);
	void onDebuggerDetached(DebuggerEvent event);
}

