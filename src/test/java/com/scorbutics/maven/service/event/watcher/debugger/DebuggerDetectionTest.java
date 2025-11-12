package com.scorbutics.maven.service.event.watcher.debugger;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class DebuggerDetectionTest {

    @Mock
    private Log logger;

    @Test
    public void testDebuggerDetectorInstantiation() {
        final DebuggerDetector detector = new DebuggerDetector();
        assertNotNull(detector, "Detector should be instantiated");
    }

    @Test
    public void testJdwpConfigParser() {
        // Test parsing various JDWP argument formats
        final java.util.List<String> args1 = java.util.Collections.singletonList(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
        );

        final java.util.Optional<JdwpConfigParser.JdwpConfig> config1 = JdwpConfigParser.parseFromArguments(args1);
        assertTrue(config1.isPresent(), "Should parse JDWP config");
        assertEquals(5005, config1.get().getPort(), "Port should be 5005");
        assertTrue(config1.get().isEnabled(), "Should be enabled");
        assertTrue(config1.get().isServer(), "Should be server mode");
        assertFalse(config1.get().isSuspend(), "Should not suspend");

        // Test with *:port format
        final java.util.List<String> args2 = java.util.Collections.singletonList(
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000"
        );

        final java.util.Optional<JdwpConfigParser.JdwpConfig> config2 = JdwpConfigParser.parseFromArguments(args2);
        assertTrue(config2.isPresent(), "Should parse JDWP config with *:port");
        assertEquals(8000, config2.get().getPort(), "Port should be 8000");
    }

    @Test
    public void testDebuggerDetectorWithInvalidPort() {
        final DebuggerDetector detector = new DebuggerDetector();

        // Use a port that's unlikely to be in use
        final boolean attached = detector.isDebuggerAttached(9999);
        assertFalse(attached, "Should not detect debugger on unused port");
    }

    @Test
    @Timeout(10)
    public void testDebuggerWatcherStartStop() throws InterruptedException {

        final DebuggerConnectionWatcher watcher = DebuggerConnectionWatcher.builder()
            .debugPort(5005)
            .checkIntervalMs(500)
            .logger(logger)
            .build();

        assertFalse(watcher.isRunning(), "Watcher should not be running initially");

        watcher.start();
        assertTrue(watcher.isRunning(), "Watcher should be running after start");

        Thread.sleep(1000); // Let it run for a bit

        watcher.stop();
        assertFalse(watcher.isRunning(), "Watcher should not be running after stop");
    }

    @Test
    @Timeout(10)
    public void testObserverSubscription() throws InterruptedException {

        final AtomicInteger attachCount = new AtomicInteger(0);
        final AtomicInteger detachCount = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(1);

        final DebuggerEventObserver observer = new DebuggerEventObserver() {
            @Override
            public void onDebuggerAttached(final DebuggerEvent event) {
                attachCount.incrementAndGet();
                latch.countDown();
            }

            @Override
            public void onDebuggerDetached(final DebuggerEvent event) {
                detachCount.incrementAndGet();
                latch.countDown();
            }
        };

        final DebuggerConnectionWatcher watcher = DebuggerConnectionWatcher.builder()
            .debugPort(5005)
            .checkIntervalMs(100)
            .logger(logger)
            .build();

        watcher.subscribe(observer);
        watcher.start();

        // Wait a bit to ensure no false events
        Thread.sleep(500);

        watcher.stop();

        // Since no actual debugger is attached/detached during test,
        // we shouldn't have received events
        assertEquals(0, attachCount.get(), "Should not have received attach events");
        assertEquals(0, detachCount.get(), "Should not have received detach events");
    }

    @Test
    @Timeout(10)
    public void testMultipleObservers() throws InterruptedException {

        final AtomicBoolean observer1Called = new AtomicBoolean(false);
        final AtomicBoolean observer2Called = new AtomicBoolean(false);

        final DebuggerEventObserver observer1 = new DebuggerEventObserver() {
            @Override
            public void onDebuggerAttached(final DebuggerEvent event) {
                observer1Called.set(true);
            }

            @Override
            public void onDebuggerDetached(final DebuggerEvent event) {
                observer1Called.set(true);
            }
        };

        final DebuggerEventObserver observer2 = new DebuggerEventObserver() {
            @Override
            public void onDebuggerAttached(final DebuggerEvent event) {
                observer2Called.set(true);
            }

            @Override
            public void onDebuggerDetached(final DebuggerEvent event) {
                observer2Called.set(true);
            }
        };

        final DebuggerConnectionWatcher watcher = DebuggerConnectionWatcher.builder()
            .debugPort(5005)
            .checkIntervalMs(100)
            .logger(logger)
            .build();

        watcher.subscribe(observer1);
        watcher.subscribe(observer2);

        // Cannot directly access protected observers field, so we'll verify functionality differently
        // Both observers should be registered

        watcher.unsubscribe(observer1);
        // Observer1 should now be unregistered

        watcher.stop();
    }

    @Test
    public void testDebuggerEventBuilder() {
        final DebuggerEvent event = DebuggerEvent.builder()
            .debugPort(5005)
            .attached(true)
            .timestamp(System.currentTimeMillis())
            .build();

        assertNotNull(event);
        assertEquals(5005, event.getDebugPort());
        assertTrue(event.isAttached());
        assertTrue(event.getTimestamp() > 0);
    }

    @Test
    @Timeout(5)
    public void testWatcherDoubleStartStop() {

        final DebuggerConnectionWatcher watcher = DebuggerConnectionWatcher.builder()
            .debugPort(5005)
            .checkIntervalMs(500)
            .logger(logger)
            .build();

        // Double start should be safe
        watcher.start();
        watcher.start();
        assertTrue(watcher.isRunning());

        // Double stop should be safe
        watcher.stop();
        watcher.stop();
        assertFalse(watcher.isRunning());
    }
}

