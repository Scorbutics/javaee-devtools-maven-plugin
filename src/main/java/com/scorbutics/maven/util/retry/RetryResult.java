package com.scorbutics.maven.util.retry;

import java.util.Optional;

import lombok.*;

/**
 * Result of a retry operation indicating success/failure and whether to retry.
 */
@Value
@AllArgsConstructor
public class RetryResult<T> {
	T value;
	boolean success;
	boolean shouldRetry;
	String failureReason;
	Exception lastException;

	public static <T> RetryResult<T> success( final T value) {
		return new RetryResult<>(value, true, false, null, null);
	}

	public static <T> RetryResult<T> retryableFailure( final String reason) {
		return new RetryResult<>(null, false, true, reason, null);
	}

	public static <T> RetryResult<T> retryableFailure( final String reason, final Exception e) {
		return new RetryResult<>(null, false, true, reason, e);
	}

	public static <T> RetryResult<T> nonRetryableFailure( final T value) {
		return new RetryResult<>(value, false, false, null, null);
	}

	public static <T> RetryResult<T> nonRetryableFailure( final T value, final String reason) {
		return new RetryResult<>(value, false, false, reason, null);
	}

	public Optional<String> getFailureReason() {
		return Optional.ofNullable(failureReason);
	}

	public Optional<Exception> getLastException() {
		return Optional.ofNullable(lastException);
	}
}