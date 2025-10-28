package com.scorbutics.maven.exception;

public class FileDeploymentException extends RuntimeException {
    public FileDeploymentException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public FileDeploymentException(final String message) {
        super(message);
    }
}
