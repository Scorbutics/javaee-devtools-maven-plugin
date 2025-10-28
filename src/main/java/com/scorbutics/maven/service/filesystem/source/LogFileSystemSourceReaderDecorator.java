package com.scorbutics.maven.service.filesystem.source;

import org.apache.maven.plugin.logging.Log;
import com.scorbutics.maven.exception.FileDeploymentException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class LogFileSystemSourceReaderDecorator implements FileSystemSourceReader {
    private final FileSystemSourceReader fileSystemSourceReader;
    private final Log logger;
    private final Path basePath;
    public LogFileSystemSourceReaderDecorator(final FileSystemSourceReader fileSystemSourceReader, final Path basePath, final Log logger) {
        this.fileSystemSourceReader = fileSystemSourceReader;
        this.logger = logger;
        this.basePath = basePath;
    }

    @Override
    public List<Path> readPattern(final Path root, final String pattern) throws FileDeploymentException {
        logger.info("Reading files from " + root + " using pattern " + pattern);
        final List<Path> paths = this.fileSystemSourceReader.readPattern(root, pattern);
        logger.info("Found " + paths.size() + " files");
        return paths;
    }

    @Override
    public boolean isDirectory(final Path path) {
        return this.fileSystemSourceReader.isDirectory(path);
    }

    @Override
    public InputStream streamRead(final Path file)
			throws
			IOException {
        return this.fileSystemSourceReader.streamRead(file);
    }

    @Override
    public boolean exists(final Path path) {
        return this.fileSystemSourceReader.exists(path);
    }

    @Override
    public void feedStreamInFile(final InputStream content, final Path path, final StandardCopyOption... options) throws IOException {
        logger.info("Copying local file to " + basePath.relativize(path));
        this.fileSystemSourceReader.feedStreamInFile(content, path, options);
    }

	@Override
	public void touchFile( final Path path ) throws IOException {
		this.fileSystemSourceReader.touchFile(path);
	}

	@Override
    public void deleteIfExists(final Path absoluteTargetPath) {
        this.fileSystemSourceReader.deleteIfExists(absoluteTargetPath);
    }

    @Override
    public void makeDirectoryOrThrow(final Path absoluteTargetDir) throws IOException {
        this.fileSystemSourceReader.makeDirectoryOrThrow(absoluteTargetDir);
    }

    @Override
    public void walkTree(final Path root, final int maxDepth, final FileVisitor<? super Path> visitor) throws IOException {
        this.fileSystemSourceReader.walkTree(root, maxDepth, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                return visitor.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                logger.debug("Visiting file: " + file);
                return visitor.visitFile(file, attrs);
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
                return visitor.visitFileFailed(file, exc);
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                return visitor.postVisitDirectory(dir, exc);
            }
        });
    }

}