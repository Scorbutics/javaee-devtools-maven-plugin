package com.scorbutics.maven.service.filesystem.local;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalFileCommonActions {

	private static void deleteDirectory( final File directory) throws IOException {
		if ( directory.exists() ) {
			if ( !directory.delete() ) {
				cleanDirectory( directory );
				if ( !directory.delete() ) {
					final String message = "Directory " + directory + " unable to be deleted.";
					throw new IOException( message );
				}
			}
		}
	}

	private static void forceDelete( final File file) throws IOException {
		if (file.isDirectory()) {
			deleteDirectory(file);
		} else {
			final boolean filePresent = file.getCanonicalFile().exists();
			if (!Files.deleteIfExists(file.toPath()) && filePresent) {
				final String message = "File " + file + " unable to be deleted.";
				throw new IOException(message);
			}
		}

	}

	private static void cleanDirectory( final File directory) throws IOException {
		if (!directory.exists()) {
			final String message = directory + " does not exist";
			throw new IllegalArgumentException(message);
		} else if (!directory.isDirectory()) {
			final String message = directory + " is not a directory";
			throw new IllegalArgumentException(message);
		} else {
			IOException exception = null;
			final File[] files = directory.listFiles();
			if (files != null) {
				for( final File file : files) {
					try {
						forceDelete(file);
					} catch ( final IOException ioe) {
						exception = ioe;
					}
				}

				if (null != exception) {
					throw exception;
				}
			}
		}
	}

    public static void deleteIfExists(final Path targetPath) {
        try {
            if (Files.isDirectory(targetPath)) {
                deleteDirectory(targetPath.toFile());
            } else {
                Files.deleteIfExists(targetPath);
            }
        } catch (final IOException e) {
            // Ignore
        }
    }

    public static void makeDirectoryOrThrow(final Path path) throws IOException {
        final File file = path.toFile();
        final boolean success = file.isDirectory() || file.mkdirs();
        if (!success && !file.isDirectory()) {
            throw new IOException("Failed to create directory " + path);
        }
    }

    public static void feedStreamInFile(final InputStream content, final Path targetPath, final StandardCopyOption[] options) throws IOException {
        try {
            Files.copy(content, targetPath, options);
        } finally {
            content.close();
        }
    }

	public static void touchFile(final Path path) throws IOException {
		if (!exists(path)) {
			Files.createFile(path);
		}
	}

    public static boolean exists(final Path path) {
        return path.toFile().exists();
    }
}