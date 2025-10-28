package com.scorbutics.maven.util.retry;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.maven.plugin.logging.*;

import lombok.*;

/**
 * Generic retry mechanism with exponential backoff strategy.
 * Executes operations that may fail due to transient issues like file locks or temporary unavailability.
 */
@Builder
@Value
public class Retryer {

	@NonNull
	Log logger;

	int    maxRetries;
	long   initialDelayMs;
	double backoffMultiplier;

	/**
	 * Executes the given operation with retry logic.
	 *
	 * @param operation
	 * 		The operation to execute
	 * @param context
	 * 		A description of what's being retried (for logging)
	 * @return The result of the operation
	 * @throws RetryException
	 * 		if all retries are exhausted
	 */
	public <T> T execute( final Supplier<RetryResult<T>> operation, final String context ) {

		int retryCount = 0;
		long currentDelayMs = initialDelayMs;

		while ( retryCount < maxRetries ) {
			final RetryResult<T> result = operation.get();

			if ( result.isSuccess() ) {
				if ( retryCount > 0 ) {
					logger.info( "Operation succeeded after " + retryCount + " retries: " + context );
				}
				return result.getValue();
			}

			// Check if we should retry
			if ( !result.isShouldRetry() ) {
				logger.debug( "Operation failed but retry not requested: " + context );
				return result.getValue();
			}

			retryCount++;

			// If we haven't exhausted retries, wait and try again
			if ( retryCount < maxRetries ) {
				logger.debug( "Retry " + retryCount +"/" + maxRetries + " for '" + context + "' in "+ currentDelayMs / 1000.0 + " seconds. Reason: " +
						result.getFailureReason().orElse( "unknown" ) );

				sleep( currentDelayMs );
				currentDelayMs = (long) ( currentDelayMs * backoffMultiplier );
			} else {
				// All retries exhausted
				final String errorMsg = String.format(
						"Maximum retries (%d) exceeded for '%s'. Last reason: %s",
						maxRetries, context, result.getFailureReason().orElse( "unknown" )
				);
				logger.error( errorMsg );
				throw new RetryException( errorMsg, result.getLastException().orElse( null ) );
			}
		}

		// This shouldn't be reached, but just in case
		throw new RetryException( "Unexpected retry loop exit for: " + context );
	}

	private void sleep( final long delayMs ) {

		try {
			TimeUnit.MILLISECONDS.sleep( delayMs );
		} catch ( final InterruptedException e ) {
			Thread.currentThread().interrupt();
			throw new RetryException( "Interrupted during retry delay", e );
		}
	}

}