package com.scorbutics.maven.util;

import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import org.apache.maven.plugin.logging.*;

import com.scorbutics.maven.service.filesystem.watcher.*;
import com.scorbutics.maven.util.path.*;

import lombok.*;

@Builder
public class WatcherEventLogger implements FileSystemEventObserver {

	private boolean verbose;
	private boolean showProgress;

	@NonNull
	private final Log logger;

	@NonNull
	private final Path sourceDir;

	private final Map<Path, FileEvent>     eventAccumulator = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler        = Executors.newSingleThreadScheduledExecutor();

	@Builder.Default
	private volatile boolean isProcessing = false;
	private ScheduledFuture<?> summaryTask;
	private ScheduledFuture<?> spinnerTask;

	private final Statistics stats = new Statistics();

	@Override
	public void onFileCreateModifyEvent( final Path fullPath ) {
		onFileEvent( StandardWatchEventKinds.ENTRY_MODIFY, fullPath);
	}

	@Override
	public void onFileDeleteEvent(final Path fullPath) {
		onFileEvent( StandardWatchEventKinds.ENTRY_DELETE, fullPath);
	}

	private void onFileEvent( final WatchEvent.Kind<?> kind, final Path fullPathFile) {
		// TODO thread-safety?
		final Path relativePath = sourceDir.relativize(fullPathFile);
		eventAccumulator.put(relativePath, new FileEvent(kind, relativePath));

		if (!isProcessing) {
			startProcessing();
		}

		resetSummaryTimer();
	}

	private void startProcessing() {
		isProcessing = true;

		if (showProgress && isInteractiveTerminal()) {
			spinnerTask = scheduler.scheduleAtFixedRate(
					this::updateSpinner,
					0, 150, TimeUnit.MILLISECONDS
			);
		} else {
			logger.info(" Processing...");
		}
	}

	private void updateSpinner() {
		if (!isProcessing || !showProgress ) return;

		final String[] frames = {"|", "/", "-", "\\"};
		final int frame = (int) ((System.currentTimeMillis() / 150) % frames.length);

		System.out.print(String.format(
				"\r%s %s Processing %05d files           ",
				timestamp(),
				frames[frame],
				eventAccumulator.size()
		));
		System.out.flush();
	}

	private void resetSummaryTimer() {
		if (summaryTask != null) {
			summaryTask.cancel(false);
		}

		summaryTask = scheduler.schedule(
				this::finishProcessing,
				1000,
				TimeUnit.MILLISECONDS
		);
	}

	private void finishProcessing() {
		isProcessing = false;

		if (spinnerTask != null) {
			spinnerTask.cancel(false);
		}

		if (eventAccumulator.isEmpty()) {
			return;
		}

		// Update statistics
		stats.addBatch(eventAccumulator.values());

		// Clear spinner line
		if (showProgress && isInteractiveTerminal()) {
			System.out.print("\r" + "                                                        " + "\r"); // Clear line
			System.out.flush();
		}

		// Display summary
		displaySummary();

		logger.info( "End of processing batch." );
		eventAccumulator.clear();
	}

	/**
	 * Check if running in interactive terminal
	 */
	private boolean isInteractiveTerminal() {
		// Check if stdout is a terminal (not redirected)
		return System.console() != null;
	}

	private void displaySummary() {
		final int total = eventAccumulator.size();

		if (verbose) {
			// Verbose: show file list
			logger.info(" -> Processed " + total + " files:");

			PathTreePrinter.prettyPrint(logger, event -> event.path, this::prettyPrintEvent , eventAccumulator.values().stream().limit( 100 ).collect( Collectors.toList()) );
			logger.info(" ... and " + (total - Math.min(total, 100)) + " more files.");

		} else {
			final Map<WatchEvent.Kind<?>, Long> counts = eventAccumulator.values().stream()
					.collect( Collectors.groupingBy(
							e -> e.kind,
							Collectors.counting()
					));

			// Normal: compact summary
			final List<String> parts = new ArrayList<>();

			parts.add( PathTreePrinter.findCommonRoot(eventAccumulator.values(), e -> e.path ).toString() );

			final Long created = counts.get(StandardWatchEventKinds.ENTRY_CREATE);
			if (created != null && created > 0) parts.add(created + " created");

			final Long modified = counts.get(StandardWatchEventKinds.ENTRY_MODIFY);
			if (modified != null && modified > 0) parts.add(modified + " modified");

			final Long deleted = counts.get(StandardWatchEventKinds.ENTRY_DELETE);
			if (deleted != null && deleted > 0) parts.add(deleted + " deleted");

			logger.info(String.format(
					"%s -> %s [Total: %d]",
					timestamp(),
					String.join(", ", parts),
					stats.getTotalFiles()
			));
		}
	}

	private String prettyPrintEvent( final FileEvent e ) {
		if (e.kind == StandardWatchEventKinds.ENTRY_CREATE) return "+";
		else if (e.kind == StandardWatchEventKinds.ENTRY_MODIFY) return "+";
		else if (e.kind == StandardWatchEventKinds.ENTRY_DELETE) return "-";
		return "?";
	}

	private String timestamp() {
		return LocalDateTime.now().format( DateTimeFormatter.ofPattern("HH:mm:ss"));
	}

	static class Statistics {
		private final AtomicInteger totalFiles = new AtomicInteger(0);
		private final AtomicInteger totalCreated = new AtomicInteger(0);
		private final AtomicInteger totalModified = new AtomicInteger(0);
		private final AtomicInteger totalDeleted = new AtomicInteger(0);

		void addBatch( final Collection<FileEvent> events) {
			events.forEach(e -> {
				totalFiles.incrementAndGet();
				if (e.kind == StandardWatchEventKinds.ENTRY_CREATE) totalCreated.incrementAndGet();
				else if (e.kind == StandardWatchEventKinds.ENTRY_MODIFY) totalModified.incrementAndGet();
				else if (e.kind == StandardWatchEventKinds.ENTRY_DELETE) totalDeleted.incrementAndGet();
			});
		}

		int getTotalFiles() { return totalFiles.get(); }
	}

	static class FileEvent {
		final WatchEvent.Kind<?> kind;
		final Path path;
		final Instant timestamp;

		FileEvent( final WatchEvent.Kind<?> kind, final Path path) {
			this.kind = kind;
			this.path = path;
			this.timestamp = Instant.now();
		}
	}
}