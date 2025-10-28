package com.scorbutics.maven.exception;

import java.io.*;

public class FileWatcherException extends RuntimeException {
    public FileWatcherException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public FileWatcherException(final String message) {
        super(message);
    }

	public FileWatcherException(final IOException e) { super("Error while watching file", e); }
}