package com.scorbutics.maven.service.filesystem.local;

import com.scorbutics.maven.service.filesystem.watcher.FileSystemWatcher;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.*;

public class LocalFileSystemWatcher extends FileSystemWatcher {

    public LocalFileSystemWatcher(final Log logger) throws IOException {
        super(32768, FileSystems.getDefault().newWatchService(), logger);
    }

}
