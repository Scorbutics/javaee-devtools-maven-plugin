package com.scorbutics.maven.service.filesystem;

import java.io.IOException;
import java.nio.file.FileVisitor;
import java.nio.file.Path;

public interface FileWalker {
    void walkTree(Path root, int maxDepth, FileVisitor<? super Path> visitor) throws IOException;
}
