package com.scorbutics.maven.util;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.*;

public class SafeStream<T, E extends Exception> {
	private final Stream<Try<T, E>> stream;

	public SafeStream( final Stream<Try<T, E>> stream ) {
		this.stream = stream;
	}

	/**
	 * Create a TryStream from a regular stream
	 */
	public static <T, E extends Exception> SafeStream<T, E> of( final Stream<T> stream) {
		return new SafeStream<>(stream.map(Try::success));
	}

	public static <T, E extends Exception> SafeStream<T, E> concat(final SafeStream<T, E> first, final SafeStream<T, E> last) {
		return new SafeStream<>(Stream.concat(
				first.stream,
				last.stream
		));
	}

	public static SafeStream<Path, IOException> empty() {
		return new SafeStream<>(Stream.empty());
	}

	/**
	 * Map with exception handling - wraps checked exceptions
	 */
	public <R> SafeStream<R, E> tryMap( final ThrowingFunction<T, R, E> mapper) {
		return new SafeStream<>(stream.map(tryItem -> tryItem.flatMap(item -> {
			try {
				return Try.success(mapper.apply(item));
			} catch ( final Exception e) {
				return Try.failure((E) e);
			}
		})));
	}

	/**
	 * Execute action with exception handling (for void operations)
	 */
	public SafeStream<T, E> tryAccept( final ThrowingConsumer<T, E> action) {
		return new SafeStream<>(stream.map(tryItem -> tryItem.flatMap(item -> {
			try {
				action.accept(item);
				return Try.success(item);
			} catch ( final Exception e) {
				return Try.failure((E) e);
			}
		})));
	}

	/**
	 * No throwing - just log failures and continue with successes.
	 * Stream remains lazy.
	 */
	public SafeStream<T, E> logFailures( final Consumer<E> errorHandler) {
		return new SafeStream<>(stream
				.peek(tryItem -> {
					if (tryItem.isFailure()) {
						errorHandler.accept(tryItem.getError());
					}
				}));
	}

	// Type erasure magic to throw checked exception as unchecked
	@SuppressWarnings("unchecked")
	private static <E extends Throwable> void sneakyThrow(final Throwable e) throws E {
		throw (E) e;
	}

	public Stream<T> failFast() throws E {
		return failFast(Function.identity());
	}

	/**
	 * Fail-fast: Throws on the FIRST failure encountered during processing.
	 * Stream remains lazy - stops processing on first error.
	 */
	public <R extends Throwable> Stream<T> failFast(final Function<E, R> exceptionMapper) throws R {
		return stream.map(tryItem -> {
			if (tryItem.isFailure()) {
				sneakyThrow(exceptionMapper.apply(tryItem.getError()));
			}
			return tryItem.getValue();
		});
	}

	public Stream<T> failAfter() throws E {
		return failAfter(Function.identity());
	}

	/**
	 * Fail-after: Processes ALL items, collects ALL failures, then throws.
	 * Stream is eager - all items processed before throwing.
	 */
	public <R extends Throwable> Stream<T> failAfter(final Function<E, R> exceptionMapper) throws R {
		final List<Try<T, E>> collected = stream.collect(Collectors.toList());

		final List<E> failures = collected.stream()
				.filter(Try::isFailure)
				.map(Try::getError)
				.collect(Collectors.toList());

		if (!failures.isEmpty()) {
			final E first = failures.get(0);
			failures.stream().skip(1).forEach(first::addSuppressed);
			throw exceptionMapper.apply(first);
		}

		return collected.stream()
				.filter(Try::isSuccess)
				.map(Try::getValue);
	}

	/**
	 * Returns a stream with distinct successful elements (failures are kept as-is).
	 * Only successes are deduplicated based on equals/hashCode.
	 * All failures are preserved.
	 */
	public SafeStream<T, E> distinct() {
		// Separate successes and failures
		final List<Try<T, E>> collected = stream.collect(Collectors.toList());

		// Get distinct successes
		final Set<T> seenSuccesses = new LinkedHashSet<>();
		final List<Try<T, E>> distinctSuccesses = new ArrayList<>();
		final List<Try<T, E>> allFailures = new ArrayList<>();

		for ( final Try<T, E> tryItem : collected) {
			if (tryItem.isSuccess()) {
				final T value = tryItem.getValue();
				if (seenSuccesses.add(value)) {  // Returns true if not seen before
					distinctSuccesses.add(tryItem);
				}
			} else {
				allFailures.add(tryItem);
			}
		}

		// Combine: distinct successes + all failures
		return new SafeStream<>(
				Stream.concat(distinctSuccesses.stream(), allFailures.stream())
		);
	}

	// Functional interfaces that can throw
	@FunctionalInterface
	public interface ThrowingFunction<T, R, E extends Exception> {
		R apply(T t) throws E;
	}

	@FunctionalInterface
	public interface ThrowingConsumer<T, E extends Exception> {
		void accept(T t) throws E;
	}

	// Try monad
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	@Getter
	public static class Try<T, E extends Exception> {
		private final T value;
		private final E error;

		public static <T, E extends Exception> Try<T, E> success(final T value) {
			return new Try<>(value, null);
		}

		public static <T, E extends Exception> Try<T, E> failure(final E error) {
			return new Try<>(null, error);
		}

		public boolean isSuccess() {
			return error == null;
		}

		public boolean isFailure() {
			return error != null;
		}

		public <R> Try<R, E> flatMap( final Function<T, Try<R, E>> mapper) {
			if (isFailure()) {
				return Try.failure(error);
			}
			return mapper.apply(value);
		}
	}

	/**
	 * Returns true if at least one item was processed successfully.
	 * Terminal operation - processes entire stream.
	 */
	public boolean anySuccess() {
		return stream.anyMatch(Try::isSuccess);
	}

	/**
	 * Returns true if ALL items were processed successfully (no failures).
	 * Terminal operation - processes entire stream.
	 */
	public boolean allSuccess() {
		return stream.allMatch(Try::isSuccess);
	}

	/**
	 * Returns true if at least one item failed.
	 * Terminal operation - processes entire stream.
	 */
	public boolean anyFailure() {
		return stream.anyMatch(Try::isFailure);
	}

	/**
	 * Returns true if ALL items failed (no successes).
	 * Terminal operation - processes entire stream.
	 */
	public boolean allFailure() {
		return stream.allMatch(Try::isFailure);
	}

	/**
	 * Returns true if there are no failures (empty stream = true).
	 * Alias for allSuccess() for better readability in some contexts.
	 */
	public boolean noFailures() {
		return allSuccess();
	}

	/**
	 * Returns the count of successful items.
	 * Terminal operation - processes entire stream.
	 */
	public long countSuccess() {
		return stream.filter(Try::isSuccess).count();
	}

	/**
	 * Returns the count of failed items.
	 * Terminal operation - processes entire stream.
	 */
	public long countFailures() {
		return stream.filter(Try::isFailure).count();
	}
}