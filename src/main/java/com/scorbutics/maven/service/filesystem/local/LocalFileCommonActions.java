package com.scorbutics.maven.service.filesystem.local;

import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalFileCommonActions {
    public static void deleteIfExists(final Path targetPath) {
        try {
            if (Files.isDirectory(targetPath)) {
                FileUtils.deleteDirectory(targetPath.toFile());
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