package com.scorbutics.maven.exception;

public class DebuggerDetectionException extends Exception {

    public DebuggerDetectionException(final String message) {
        super(message);
    }

    public DebuggerDetectionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

