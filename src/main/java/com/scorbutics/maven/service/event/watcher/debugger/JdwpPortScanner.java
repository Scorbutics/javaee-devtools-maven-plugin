package com.scorbutics.maven.service.event.watcher.debugger;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.apache.maven.plugin.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans localhost ports to find JDWP debug ports.
 * This works with Docker containers where the JDWP port is exposed to localhost.
 * Does NOT require knowing the PID - only needs to find active JDWP ports.
 */
public class JdwpPortScanner {

    private static final int DEFAULT_PORT_SCAN_START = 5000;
    private static final int DEFAULT_PORT_SCAN_END = 9000;
    private static final int CONNECTION_TIMEOUT_MS = 100;

    // JDWP handshake response
    private static final String JDWP_HANDSHAKE = "JDWP-Handshake";

    @Builder
    @Value
    public static class JdwpPortInfo {
        int port;
        boolean isJdwp;
        String response;
    }

    @NonNull
    private final Log logger;
    private final int scanRangeStart;
    private final int scanRangeEnd;

    @Builder
    public JdwpPortScanner(final Log logger, final Integer scanRangeStart, final Integer scanRangeEnd) {
        this.logger = logger;
        this.scanRangeStart = scanRangeStart != null ? scanRangeStart : DEFAULT_PORT_SCAN_START;
        this.scanRangeEnd = scanRangeEnd != null ? scanRangeEnd : DEFAULT_PORT_SCAN_END;
    }

    /**
     * Scan for JDWP ports in the configured range.
     * This tries to connect to each port and checks for JDWP handshake.
     *
     * @return List of ports that respond to JDWP handshake
     */
    public List<Integer> scanForJdwpPorts() {
        final List<Integer> jdwpPorts = new ArrayList<>();
        logger.debug("Scanning localhost ports " + scanRangeStart + "-" + scanRangeEnd + " for JDWP...");

        for (int port = scanRangeStart; port <= scanRangeEnd; port++) {
            if (isJdwpPort(port)) {
                jdwpPorts.add(port);
                logger.debug("Found JDWP port: " + port);
            }
        }

        if (!jdwpPorts.isEmpty()) {
            logger.info("Found " + jdwpPorts.size() + " JDWP port(s): " + jdwpPorts);
        }

        return jdwpPorts;
    }

    /**
     * Find the first available JDWP port.
     * Useful when you expect only one debuggable application.
     */
    public Optional<Integer> findFirstJdwpPort() {
        final List<Integer> ports = scanForJdwpPorts();
        if (!ports.isEmpty()) {
            return Optional.of(ports.get(0));
        }
        return Optional.empty();
    }

    /**
     * Check if a specific port is a JDWP port by attempting handshake.
     *
     * @param port The port to check
     * @return true if the port responds with JDWP handshake
     */
    public boolean isJdwpPort(final int port) {
        try (final Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), CONNECTION_TIMEOUT_MS);
            socket.setSoTimeout(CONNECTION_TIMEOUT_MS);

            // Send JDWP handshake
            socket.getOutputStream().write(JDWP_HANDSHAKE.getBytes());
            socket.getOutputStream().flush();

            // Read response
            final byte[] buffer = new byte[JDWP_HANDSHAKE.length()];
            final int bytesRead = socket.getInputStream().read(buffer);

            if (bytesRead > 0) {
                final String response = new String(buffer, 0, bytesRead);
                return JDWP_HANDSHAKE.equals(response);
            }

        } catch (final SocketTimeoutException e) {
            // Port doesn't respond quickly - probably not JDWP
        } catch (final IOException e) {
            // Port not open or connection refused - not a problem
            if (e instanceof SocketException && "Connection reset".equals(e.getMessage())) {
                logger.info(port + " - Connection reset by peer, possible JDWP port with debugger already attached.");
            }
        }

        return false;
    }

    /**
     * Check if a debugger is currently attached to a JDWP port.
     * This checks for established connections on the port.
     *
     * @param port The JDWP port to check
     * @return true if a debugger connection is established
     */
    public boolean isDebuggerAttachedToPort(final int port) {
        // Check for established connections on this port
        if (com.sun.jna.Platform.isLinux()) {
            return checkLinuxPortConnections(port);
        } else if (com.sun.jna.Platform.isWindows()) {
            return checkWindowsPortConnections(port);
        }
        return false;
    }

    /**
     * Check for established connections on Linux using netstat
     */
    private boolean checkLinuxPortConnections(final int port) {
        try {
            // Use ss or netstat to check for ESTABLISHED connections
            final ProcessBuilder pb = new ProcessBuilder("sh", "-c",
                    "ss -tn state established 2>/dev/null || netstat -tn 2>/dev/null | grep ESTABLISHED");
            final Process process = pb.start();

            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                final Pattern portPattern = Pattern.compile(":(\\d+)\\s");

                while ((line = reader.readLine()) != null) {
                    final Matcher matcher = portPattern.matcher(line);
                    while (matcher.find()) {
                        try {
                            final int foundPort = Integer.parseInt(matcher.group(1));
                            if (foundPort == port && line.contains("ESTAB")) {
                                return true;
                            }
                        } catch (final NumberFormatException e) {
                            // Ignore
                        }
                    }
                }
            }

            process.waitFor();
        } catch (final IOException | InterruptedException e) {
            logger.debug("Failed to check port connections: " + e.getMessage());
        }

        return false;
    }

    /**
     * Check for established connections on Windows using netstat
     */
    private boolean checkWindowsPortConnections(final int port) {
        try {
            final ProcessBuilder pb = new ProcessBuilder("netstat", "-an");
            final Process process = pb.start();

            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                final String portStr = ":" + port;

                while ((line = reader.readLine()) != null) {
                    if (line.contains(portStr) && line.contains("ESTABLISHED")) {
                        return true;
                    }
                }
            }

            process.waitFor();
        } catch (final IOException | InterruptedException e) {
            logger.debug("Failed to check port connections: " + e.getMessage());
        }

        return false;
    }

    /**
     * Create a watcher for a JDWP port without needing PID.
     * This uses a special detector that only monitors port connections.
     *
     * @param port The JDWP port to monitor
     * @param checkIntervalMs How often to check (in milliseconds)
     * @param logger Maven logger
     * @return DebuggerConnectionWatcher configured for port-only monitoring
     */
    public DebuggerConnectionWatcher createPortOnlyWatcher(final int port, final long checkIntervalMs, final Log logger) {
        return DebuggerConnectionWatcher.builder()
                .debugPort(port)
                .checkIntervalMs(checkIntervalMs)
                .logger(logger)
                .build();
    }

    /**
     * Convenience method: Find first JDWP port and create a watcher for it.
     * Perfect for Docker scenarios where you have one exposed debug port.
     *
     * @param checkIntervalMs Check interval in milliseconds
     * @param logger Maven logger
     * @return Optional watcher, or empty if no JDWP port found
     */
    public Optional<DebuggerConnectionWatcher> findAndCreateWatcher(final long checkIntervalMs, final Log logger) {
        return findFirstJdwpPort()
                .map(port -> {
                    if (logger != null) {
                        logger.info("Creating debugger watcher for JDWP port: " + port);
                    }
                    return createPortOnlyWatcher(port, checkIntervalMs, logger);
                });
    }

    /**
     * Quick check: Is there any JDWP port available on localhost?
     */
    public boolean hasAnyJdwpPort() {
        return findFirstJdwpPort().isPresent();
    }
}

