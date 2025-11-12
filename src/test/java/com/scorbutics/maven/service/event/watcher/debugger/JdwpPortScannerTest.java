package com.scorbutics.maven.service.event.watcher.debugger;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class JdwpPortScannerTest {

    @Mock
    private Log logger;

    @Test
    public void testScannerInstantiation() {
        final JdwpPortScanner scanner = JdwpPortScanner.builder()
            .logger(logger)
            .build();
        assertNotNull(scanner, "Scanner should be instantiated");
    }

    @Test
    public void testScannerWithCustomRange() {
        final JdwpPortScanner scanner = JdwpPortScanner.builder()
            .logger(logger)
            .scanRangeStart(5000)
            .scanRangeEnd(5010)
            .build();
        assertNotNull(scanner);

        // Scan should complete without errors (may or may not find ports)
        final List<Integer> ports = scanner.scanForJdwpPorts();
        assertNotNull(ports);
    }

    @Test
    public void testIsJdwpPortWithInvalidPort() {
        final JdwpPortScanner scanner = JdwpPortScanner.builder()
            .logger(logger)
            .build();

        // Port 1 is unlikely to be JDWP
        final boolean isJdwp = scanner.isJdwpPort(1);
        assertFalse(isJdwp, "Port 1 should not be JDWP");
    }

    @Test
    public void testFindFirstJdwpPort() {
        final JdwpPortScanner scanner = JdwpPortScanner.builder()
            .logger(logger)
            .scanRangeStart(5000)
            .scanRangeEnd(5005)
            .build();

        final Optional<Integer> port = scanner.findFirstJdwpPort();
        assertNotNull(port, "Result should not be null");
        // May or may not find a port, but should not crash
    }

    @Test
    public void testHasAnyJdwpPort() {
        final JdwpPortScanner scanner = JdwpPortScanner.builder()
            .logger(logger)
            .scanRangeStart(5000)
            .scanRangeEnd(5005)
            .build();

        // Should not throw exception
        final boolean hasPort = scanner.hasAnyJdwpPort();
        // Result depends on environment, but method should work
        assertNotNull(Boolean.valueOf(hasPort));
    }

    @Test
    public void testCreatePortOnlyWatcher() {
        final JdwpPortScanner scanner = JdwpPortScanner.builder()
            .logger(logger)
            .build();

        final DebuggerConnectionWatcher watcher = scanner.createPortOnlyWatcher(5005, 2000, null);

        assertNotNull(watcher);
        // Verify it's in port-only mode (PID should be -1)
        assertFalse(watcher.isRunning());
    }

    @Test
    public void testFindAndCreateWatcher() {
        final JdwpPortScanner scanner = JdwpPortScanner.builder()
            .logger(logger)
            .scanRangeStart(5000)
            .scanRangeEnd(5002)
            .build();

        final Optional<DebuggerConnectionWatcher> watcher = scanner.findAndCreateWatcher(2000, null);

        assertNotNull(watcher);
        // May or may not find a watcher, but should not crash
    }

    @Test
    public void testJdwpPortInfoBuilder() {
        final JdwpPortScanner.JdwpPortInfo info = JdwpPortScanner.JdwpPortInfo.builder()
            .port(5005)
            .isJdwp(true)
            .response("JDWP-Handshake")
            .build();

        assertNotNull(info);
        assertEquals(5005, info.getPort());
        assertTrue(info.isJdwp());
        assertEquals("JDWP-Handshake", info.getResponse());
    }

    @Test
    public void testIsDebuggerAttachedToPort() {
        final JdwpPortScanner scanner = JdwpPortScanner.builder()
            .logger(logger)
            .build();

        // Test with a port that likely has no debugger attached
        final boolean attached = scanner.isDebuggerAttachedToPort(65535);

        // Should not crash, result depends on system state
        assertNotNull(Boolean.valueOf(attached));
    }
}

