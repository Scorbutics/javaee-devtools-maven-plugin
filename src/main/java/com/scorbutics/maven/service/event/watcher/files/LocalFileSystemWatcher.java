package com.scorbutics.maven.service.event.watcher.files;

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.*;

public class LocalFileSystemWatcher extends FileSystemWatcher {

    public LocalFileSystemWatcher(final int debounceWindowMs, final Log logger) throws IOException {
        super(debounceWindowMs, 32768, FileSystems.getDefault().newWatchService(), logger);
    }

}