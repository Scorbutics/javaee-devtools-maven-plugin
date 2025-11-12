package com.scorbutics.maven.service.event.watcher.debugger;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Service to detect if a debugger is actively attached to a JVM process via JDWP.
 * This implementation supports both Linux and Windows platforms.
 */
public class DebuggerDetector {

    /**
     * Checks if a debugger is actively attached to the specified JVM process
     * 
     * @param debugPort the JDWP port to check for connections
     * @return true if a debugger is attached, false otherwise
     */
    public boolean isDebuggerAttached(final int debugPort) {
        return hasEstablishedConnectionOnPort(debugPort);
    }

    /**
     * Network byte order to host short
     */
    private int ntohs(final short netShort) {
        return ((netShort & 0xff) << 8) | ((netShort >> 8) & 0xff);
    }

    /**
     * Check for established TCP connections on the debug port (port-only mode, no PID)
     */
    private boolean hasEstablishedConnectionOnPort(final int debugPort) {
        if (Platform.isLinux()) {
            return checkLinuxPortOnly(debugPort);
        } else if (Platform.isWindows()) {
            return checkWindowsPortOnly(debugPort);
        } else {
            return false;
        }
    }

    /**
     * Check for connections on Linux without PID (port-only mode for Docker)
     */
    private boolean checkLinuxPortOnly(final int debugPort) {
        try (final BufferedReader reader = new BufferedReader(new FileReader("/proc/net/tcp"))) {
            String line;
            reader.readLine(); // Skip header

            while ((line = reader.readLine()) != null) {
                final String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) continue;

                final String localAddress = parts[1];
                final String state = parts[3];

                // Parse local port
                final String[] addressParts = localAddress.split(":");
                final int localPort = Integer.parseInt(addressParts[1], 16);

                // Check if it's our debug port and state is ESTABLISHED (01)
                if (localPort == debugPort && "01".equals(state)) {
                    return true;
                }
            }
        } catch (final IOException e) {
            // Ignore
        }

        // Try IPv6
        try (final BufferedReader reader = new BufferedReader(new FileReader("/proc/net/tcp6"))) {
            String line;
            reader.readLine(); // Skip header

            while ((line = reader.readLine()) != null) {
                final String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) continue;

                final String localAddress = parts[1];
                final String state = parts[3];

                // Parse local port
                final String[] addressParts = localAddress.split(":");
                final int localPort = Integer.parseInt(addressParts[1], 16);

                // Check if it's our debug port and state is ESTABLISHED (01)
                if (localPort == debugPort && "01".equals(state)) {
                    return true;
                }
            }
        } catch (final IOException e) {
            // Ignore
        }

        return false;
    }

    /**
     * Check for connections on Windows without PID (port-only mode for Docker)
     */
    private boolean checkWindowsPortOnly(final int debugPort) {
        try {
            final Iphlpapi iphlpapi = Native.load("Iphlpapi", Iphlpapi.class);

            final IntByReference size = new IntByReference(0);
            iphlpapi.GetExtendedTcpTable(null, size, false, AF_INET, TCP_TABLE_OWNER_PID_ALL, 0);

            final Pointer pTcpTable = new com.sun.jna.Memory(size.getValue());
            final int result = iphlpapi.GetExtendedTcpTable(pTcpTable, size, false, AF_INET, TCP_TABLE_OWNER_PID_ALL, 0);

            if (result == 0) {
                final int numEntries = pTcpTable.getInt(0);
                int offset = 4;

                for (int i = 0; i < numEntries; i++) {
                    final int localPort = ntohs(pTcpTable.getShort(offset + 4));
                    final int state = pTcpTable.getInt(offset + 8);

                    // MIB_TCP_STATE_ESTAB = 5
                    if (localPort == debugPort && state == 5) {
                        return true;
                    }

                    offset += 24;
                }
            }
        } catch (final Exception e) {
            // Error accessing Windows API
        }

        return false;
    }

    // Windows constants
    private static final int AF_INET = 2;
    private static final int TCP_TABLE_OWNER_PID_ALL = 5;

    /**
     * JNA interface for Windows Iphlpapi.dll
     */
    public interface Iphlpapi extends Library {
        int GetExtendedTcpTable(
            Pointer pTcpTable,
            IntByReference pdwSize,
            boolean bOrder,
            int ulAf,
            int TableClass,
            int Reserved
        );
    }
}

