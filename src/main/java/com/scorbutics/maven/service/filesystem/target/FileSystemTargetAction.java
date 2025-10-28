package com.scorbutics.maven.service.filesystem.target;

import com.scorbutics.maven.service.filesystem.FileSystemCommonActions;
import com.scorbutics.maven.service.filesystem.FileWalker;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public interface FileSystemTargetAction extends FileWalker, FileSystemCommonActions {

    OutputStream streamWrite(Path file) throws FileNotFoundException;

    void moveFile(Path source, Path destination);
}