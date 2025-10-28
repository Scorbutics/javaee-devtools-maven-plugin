package com.scorbutics.maven.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import org.apache.maven.plugin.logging.*;

public class TimedTask<T> {
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
	private final Map<T, Object> locks = new ConcurrentHashMap<>();
	private final Map<T, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
	private final long timeoutMillis;
	private final Consumer<T> callback;

	public TimedTask(final long timeoutMillis, final Consumer<T> callback) {
		this.timeoutMillis = timeoutMillis;
		this.callback = callback;
	}

	public void overrideAndTrigger(final T key) {
		// Get or create a lock object for this specific key
		final Object lock = locks.computeIfAbsent(key, k -> new Object());

		synchronized (lock) {
			// Cancel and replace in one operation
			timers.computeIfPresent( key, ( k, timer ) -> {
				timer.cancel( false );
				return null;
			} );

			// Schedule new timer
			timers.put( key, scheduler.schedule(
					() -> callback.accept(key),
					timeoutMillis,
					TimeUnit.MILLISECONDS
			) );
		}
	}

	public void shutdown() {
		timers.values().forEach(timer -> timer.cancel(false));
		scheduler.shutdown();
	}
}