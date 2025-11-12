package com.scorbutics.maven.service.event.watcher.debugger;

import com.scorbutics.maven.service.event.observer.ObservableQueue;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.maven.plugin.logging.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watcher that periodically checks for debugger attachment/detachment and notifies observers.
 * This class follows the observer pattern to allow loose coupling with the rest of the system.
 */
public class DebuggerConnectionWatcher extends ObservableQueue<DebuggerEventObserver> implements DebuggerEventObservable {

    private final DebuggerDetector detector;
    private final int debugPort;
    private final long checkIntervalMs;
    @NonNull
    private final Log logger;
    private final AtomicBoolean lastKnownState;
    private ScheduledExecutorService scheduler;
    /**
     * -- GETTER --
     *  Check if currently monitoring
     */
    @Getter
    private volatile boolean running;

    @Builder
    public DebuggerConnectionWatcher(final int debugPort, final long checkIntervalMs, final Log logger) {
        this.detector = new DebuggerDetector();
        this.debugPort = debugPort;
        this.checkIntervalMs = checkIntervalMs;
        this.logger = logger;
        this.lastKnownState = new AtomicBoolean(false);
        this.running = false;
    }

    /**
     * Start monitoring for debugger attachment/detachment
     */
    public void start() {
        if (running) {
            return;
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread thread = new Thread(r, "DebuggerConnectionWatcher");
            thread.setDaemon(true);
            return thread;
        });

        // Initial check
        final boolean initialState = detector.isDebuggerAttached(debugPort);
        lastKnownState.set(initialState);
        
        if (initialState) {
            logger.info("Debugger is currently attached on port " + debugPort);
        }

        // Schedule periodic checks
        scheduler.scheduleAtFixedRate(
            this::checkDebuggerState,
            checkIntervalMs,
            checkIntervalMs,
            TimeUnit.MILLISECONDS
        );

        logger.info("Started debugger connection monitoring (Port: " + debugPort + ")");
    }

    /**
     * Stop monitoring
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (final InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Stopped debugger connection monitoring");
    }

    /**
     * Check current debugger state and notify observers if changed
     */
    private void checkDebuggerState() {
        try {
            final boolean currentState = detector.isDebuggerAttached(debugPort);
            final boolean previousState = lastKnownState.getAndSet(currentState);

            if (currentState != previousState) {
                final DebuggerEvent event = DebuggerEvent.builder()
                    .debugPort(debugPort)
                    .attached(currentState)
                    .timestamp(System.currentTimeMillis())
                    .build();

                if (currentState) {
                    notifyDebuggerAttached(event);
                } else {
                    notifyDebuggerDetached(event);
                }
            }
        } catch (final Exception e) {
            logger.warn("Error checking debugger state: " + e.getMessage());
        }
    }

    @Override
    public void notifyDebuggerAttached(final DebuggerEvent event) {
        logger.info("Debugger ATTACHED detected on port " + event.getDebugPort());
        for (final DebuggerEventObserver observer : observers) {
            try {
                observer.onDebuggerAttached(event);
            } catch (final Exception e) {
                logger.error("Error notifying observer of debugger attachment", e);
            }
        }
    }

    @Override
    public void notifyDebuggerDetached(final DebuggerEvent event) {
        logger.info("Debugger DETACHED detected on port " + event.getDebugPort());
        for (final DebuggerEventObserver observer : observers) {
            try {
                observer.onDebuggerDetached(event);
            } catch (final Exception e) {
                logger.error("Error notifying observer of debugger detachment", e);
            }
        }
    }

    /**
     * Get the last known debugger state
     */
    public boolean isDebuggerCurrentlyAttached() {
        return lastKnownState.get();
    }
}

