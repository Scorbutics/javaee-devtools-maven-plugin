package com.scorbutics.maven.service.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public interface FileSystemCommonActions {
    void feedStreamInFile(final InputStream content, final Path targetPath, final StandardCopyOption... options) throws IOException;

	void touchFile(Path path) throws IOException;

    void deleteIfExists(Path absoluteTargetPath);

    void makeDirectoryOrThrow(Path absoluteTargetDir) throws IOException;

    boolean exists(Path path);
}