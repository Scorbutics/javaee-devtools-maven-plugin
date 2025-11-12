package com.scorbutics.maven.service.event.watcher.debugger;

import lombok.Builder;
import lombok.Value;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Optional;

/**
 * Utility class to parse JDWP configuration from JVM arguments.
 * This helps automatically detect the debug port configuration.
 */
public class JdwpConfigParser {

    @Builder
    @Value
    public static class JdwpConfig {
        boolean enabled;
        int port;
        String transport;
        boolean server;
        boolean suspend;
    }

    /**
     * Parse JDWP configuration from the current JVM's input arguments
     */
    public static Optional<JdwpConfig> parseFromCurrentJvm() {
        final List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        return parseFromArguments(arguments);
    }

    /**
     * Parse JDWP configuration from a list of JVM arguments
     */
    public static Optional<JdwpConfig> parseFromArguments(final List<String> arguments) {
        for (final String arg : arguments) {
            if (arg.contains("jdwp")) {
                final Optional<JdwpConfig> config = parseJdwpArgument(arg);
                if (config.isPresent()) {
                    return config;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Parse a single JDWP argument string
     */
    private static Optional<JdwpConfig> parseJdwpArgument(final String arg) {
        if (!arg.contains("jdwp=")) {
            return Optional.empty();
        }

        try {
            final String config = arg.substring(arg.indexOf("jdwp=") + 5);
            final String[] parts = config.split(",");

            int port = -1;
            String transport = "dt_socket";
            boolean server = true;
            boolean suspend = true;

            for (final String part : parts) {
                final String[] keyValue = part.split("=");
                if (keyValue.length != 2) continue;

                final String key = keyValue[0].trim();
                final String value = keyValue[1].trim();

                switch (key) {
                    case "address":
                        port = parseAddress(value);
                        break;
                    case "transport":
                        transport = value;
                        break;
                    case "server":
                        server = "y".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
                        break;
                    case "suspend":
                        suspend = "y".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
                        break;
                }
            }

            if (port > 0) {
                return Optional.of(JdwpConfig.builder()
                    .enabled(true)
                    .port(port)
                    .transport(transport)
                    .server(server)
                    .suspend(suspend)
                    .build());
            }
        } catch (final Exception e) {
            // Failed to parse
        }

        return Optional.empty();
    }

    /**
     * Parse the address field which can be just a port or host:port
     */
    private static int parseAddress(final String address) {
        try {
            // Try to parse as plain port number
            return Integer.parseInt(address);
        } catch (final NumberFormatException e) {
            // Try to parse as host:port
            if (address.contains(":")) {
                final String[] parts = address.split(":");
                if (parts.length == 2) {
                    return Integer.parseInt(parts[1]);
                }
            }
            // Try to parse as *:port (listen on all interfaces)
            if (address.startsWith("*:")) {
                return Integer.parseInt(address.substring(2));
            }
        }
        return -1;
    }

}

