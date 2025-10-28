package com.scorbutics.maven.service.filesystem.target;

import org.apache.maven.plugin.logging.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class LogFileSystemTargetActionDecorator implements FileSystemTargetAction {
    private final FileSystemTargetAction fileSystemTargetAction;
    private final Log logger;
    private final Path baseTargetPath;

    public LogFileSystemTargetActionDecorator(final FileSystemTargetAction fileSystemTargetAction, final Path baseTargetPath, final Log logger) {
        this.fileSystemTargetAction = fileSystemTargetAction;
        this.baseTargetPath = baseTargetPath;
        this.logger = logger;
    }

    @Override
    public void feedStreamInFile(final InputStream content, final Path targetPath, final StandardCopyOption... options) throws IOException {
        logger.info("Copying file to " + baseTargetPath.relativize(targetPath));
        this.fileSystemTargetAction.feedStreamInFile(content, targetPath, options);
    }

    @Override
    public void deleteIfExists(final Path targetPath) {
        logger.info("Deleting file " + baseTargetPath.relativize(targetPath));
        this.fileSystemTargetAction.deleteIfExists(targetPath);
    }

    @Override
    public void touchFile(final Path path) throws IOException {
        logger.info("Touching file " + baseTargetPath.relativize(path));
        this.fileSystemTargetAction.touchFile(path);
    }

    @Override
    public OutputStream streamWrite(final Path file) throws FileNotFoundException {
        return this.fileSystemTargetAction.streamWrite(file);
    }

    @Override
    public void makeDirectoryOrThrow(final Path path) throws IOException {
        logger.debug("Making directory " + path);
        this.fileSystemTargetAction.makeDirectoryOrThrow(path);
    }

    @Override
    public boolean exists(final Path path) {
        return this.fileSystemTargetAction.exists(path);
    }

    @Override
    public void moveFile(final Path source, final Path destination) {
        this.fileSystemTargetAction.moveFile(source, destination);
    }

    @Override
    public void walkTree(final Path root, final int maxDepth, final FileVisitor<? super Path> visitor) throws IOException {
        this.fileSystemTargetAction.walkTree(root, maxDepth, new SimpleFileVisitor<Path>() {
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
                logger.error("Failed to visit file: " + file, exc);
                return visitor.visitFileFailed(file, exc);
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                return visitor.postVisitDirectory(dir, exc);
            }
        });
    }
}