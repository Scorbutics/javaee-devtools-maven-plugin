package com.scorbutics.maven.service.filesystem.watcher;

import com.scorbutics.maven.service.filesystem.FileSystemCommonActions;
import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;
import com.scorbutics.maven.util.retry.*;

import lombok.AllArgsConstructor;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.*;

public class FileLockCheckerAndRetryer {

    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_DELAY_MS = 500; // 1 second

    private final FileSystemCommonActions fsActions;
    private final FileSystemSourceReader fileSystemSourceReader;
	private final Retryer retryer;

	public FileLockCheckerAndRetryer(
			final FileSystemCommonActions fsActions,
			final FileSystemSourceReader fileSystemSourceReader,
			final Log logger) {

		this.fsActions = fsActions;
		this.fileSystemSourceReader = fileSystemSourceReader;
		retryer = Retryer.builder()
				.maxRetries( MAX_RETRIES )
				.logger( logger )
				.initialDelayMs( INITIAL_DELAY_MS )
				.backoffMultiplier( 2.0 )
				.build()
		;
	}

    interface FileOperation {
        void execute() throws IOException, InterruptedException;
    }

    // TODO probably too specific to local filesystem - move to local fs package?
    private void doOnFileWithLockCheck(
			final Path filePath,
			final StandardOpenOption[] openOptions,
			final Boolean isDirectory,
			final FileOperation operation) throws RetryException {
		retryer.execute(() -> {
			// Prepare file if needed
			if (Arrays.asList(openOptions).contains(StandardOpenOption.CREATE)) {
				final Optional<RetryResult<Object>> result = prepareFileForWriting(filePath);
				if (result.isPresent() && !result.get().isSuccess()) {
					return result.get();
				}
			}

			try {
				// If file didn't exist before, skip locking as it can't be locked
				// If it's a directory, skip locking as well
				if (isDirectory == null || isDirectory) {
					operation.execute();
					return RetryResult.success(null);
				}

				try (final FileChannel ignored = FileChannel.open(filePath, openOptions)) {
					// Lock acquired, now perform the file operation
					operation.execute();
					return RetryResult.success(null);
				} catch (final OverlappingFileLockException e) {
					// This happens if another thread within the *same* JVM
					// already holds a lock on the file.
					return RetryResult.retryableFailure(
							"An internal lock already exists", e);
				}
			} catch (final Exception e) {
				return RetryResult.retryableFailure(
						"Operation failed: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
			}
		}, Arrays.asList(openOptions) + " file: " + filePath);

    }

	private Optional<RetryResult<Object>> prepareFileForWriting( final Path filePath ) {
		try {
			fsActions.makeDirectoryOrThrow(filePath.getParent());
			if (!fsActions.exists(filePath)) {
				fsActions.touchFile(filePath);
			}
		} catch ( final IOException e) {
			return Optional.of(RetryResult.retryableFailure("Failed to prepare file: " + e.getMessage(), e));
		}
		return Optional.empty();
	}

	public void copyFileWithLockCheck(final Path sourcePath, final Path targetPath, final StandardCopyOption copyOption) throws IOException, RetryException {
        final Boolean isDirectory = !fileSystemSourceReader.exists(sourcePath) ? null : fileSystemSourceReader.isDirectory(sourcePath);
        doOnFileWithLockCheck(targetPath, new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE}, isDirectory, () -> {
            doOnFileWithLockCheck(sourcePath, new StandardOpenOption[]{StandardOpenOption.READ}, isDirectory, () -> {
                fsActions.feedStreamInFile(fileSystemSourceReader.streamRead(sourcePath), targetPath, copyOption);
            });
        });
    }

    public void deleteIfExists(final Path filePath) throws RetryException {
		fsActions.deleteIfExists(filePath);
    }

}