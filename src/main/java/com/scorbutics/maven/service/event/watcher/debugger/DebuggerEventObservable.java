package com.scorbutics.maven.service.event.watcher.debugger;

public interface DebuggerEventObservable {
	void notifyDebuggerAttached(DebuggerEvent event);
	void notifyDebuggerDetached(DebuggerEvent event);
}
