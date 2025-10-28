package com.scorbutics.maven.util.retry;

/**
 * Exception thrown when all retry attempts are exhausted.
 */
public class RetryException extends RuntimeException {
	public RetryException( final String message) {
		super(message);
	}

	public RetryException( final String message, final Throwable cause) {
		super(message, cause);
	}
}