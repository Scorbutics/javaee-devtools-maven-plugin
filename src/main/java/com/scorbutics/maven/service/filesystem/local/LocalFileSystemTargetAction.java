package com.scorbutics.maven.service.filesystem.local;

import com.scorbutics.maven.service.filesystem.target.FileSystemTargetAction;

import java.io.*;
import java.nio.file.*;
import java.util.EnumSet;

public class LocalFileSystemTargetAction implements FileSystemTargetAction {

    @Override
    public void feedStreamInFile(final InputStream content, final Path targetPath, final StandardCopyOption[] options) throws IOException {
        LocalFileCommonActions.feedStreamInFile(content, targetPath, options);
    }

    @Override
    public void deleteIfExists(final Path targetPath) {
        LocalFileCommonActions.deleteIfExists(targetPath);
    }

    @Override
    public void touchFile(final Path path) throws IOException {
		LocalFileCommonActions.touchFile(path);
    }

    @Override
    public OutputStream streamWrite(final Path file) throws FileNotFoundException {
        return new FileOutputStream(file.toFile());
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
    public boolean exists(final Path path) {
        return LocalFileCommonActions.exists(path);
    }

    @Override
    public void moveFile(final Path source, final Path destination) {
        source.toFile().renameTo(destination.toFile());
    }
}