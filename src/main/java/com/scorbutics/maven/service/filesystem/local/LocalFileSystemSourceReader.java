package com.scorbutics.maven.service.filesystem.local;

import com.scorbutics.maven.exception.FileDeploymentException;
import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class LocalFileSystemSourceReader implements FileSystemSourceReader {


    /*
     * We parse the source directory with the NIO DirectoryStream to detect all files supporting wildcards
     *
     * e.g. *.{war,ear,jar} e.g. *.war
     */
    @Override
    public List<Path> readPattern(final Path root, final String pattern) throws FileDeploymentException {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(root, pattern)) {
            return stream != null ? StreamSupport.stream(stream.spliterator(), false).collect(Collectors.toList()) : Collections.emptyList();
        } catch (final Exception e) {
            throw new FileDeploymentException("Error while reading directory at '" + root + "' using pattern '" + pattern + "'", e);
        }
    }

    @Override
    public boolean isDirectory(final Path path) {
        return path != null && path.toFile().isDirectory();
    }

    @Override
    public InputStream streamRead(final Path file)
			throws
			IOException {
        return Files.newInputStream( file.toFile().toPath() );
    }

    @Override
    public boolean exists(final Path path) {
        return LocalFileCommonActions.exists(path);
    }

    @Override
    public void feedStreamInFile(final InputStream content, final Path targetPath, final StandardCopyOption... options) throws IOException {
        LocalFileCommonActions.feedStreamInFile(content, targetPath, options);
    }

    @Override
    public void deleteIfExists(final Path path) {
        LocalFileCommonActions.deleteIfExists(path);
    }

    @Override
    public void makeDirectoryOrThrow(final Path path) throws IOException {
        LocalFileCommonActions.makeDirectoryOrThrow(path);
    }

    @Override
    public void walkTree(final Path root, final int maxDepth, final FileVisitor<? super Path> visitor) throws IOException {
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), maxDepth, visitor);
    }

	@Override
	public void touchFile(final Path path) throws IOException {
		LocalFileCommonActions.touchFile(path);
	}

}