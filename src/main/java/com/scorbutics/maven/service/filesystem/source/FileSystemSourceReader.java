package com.scorbutics.maven.service.filesystem.source;

import com.scorbutics.maven.exception.FileDeploymentException;
import com.scorbutics.maven.service.filesystem.FileSystemCommonActions;
import com.scorbutics.maven.service.filesystem.FileWalker;
import com.scorbutics.maven.service.filesystem.watcher.RecursiveDirectoryWatcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

public interface FileSystemSourceReader extends FileWalker, FileSystemCommonActions {
    List<Path> readPattern(Path root, String pattern) throws FileDeploymentException;

    boolean isDirectory(Path path);

    InputStream streamRead(Path file)
			throws
			IOException;

}