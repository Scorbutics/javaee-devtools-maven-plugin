package com.scorbutics.maven.service.filesystem;

import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;
import com.scorbutics.maven.service.filesystem.target.FileSystemTargetAction;
import org.apache.maven.plugin.logging.Log;

import java.io.*;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Unzipper {

    private final FileSystemTargetAction fileSystemTargetAction;
    private final FileSystemSourceReader fileSystemSourceReader;
    private final HashSet<String> nestedUnpackNames;
    private final Log logger;

    public Unzipper(final FileSystemSourceReader fileSystemSourceReader, final FileSystemTargetAction fileSystemTargetAction, final Collection<String> nestedUnpackNames, final Log logger) {
        this.fileSystemSourceReader = fileSystemSourceReader;
        this.fileSystemTargetAction = fileSystemTargetAction;
        this.nestedUnpackNames = new HashSet<>(nestedUnpackNames);
        this.logger = logger;
    }

    /**
     * Helper Method to unzip an artifact
     *
     * @see <a href="https://www.baeldung.com/java-compress-and-uncompress">Java compress and uncompress</a>
     * @param fileZip - artifact
     * @param target  - target folder
     * @throws IOException
     */
    public void unzipArtifact(final Path fileZip, final Path target) throws IOException {
        try (final InputStream is = this.fileSystemSourceReader.streamRead(fileZip)) {
            extract(is, target, 0);
        }
    }

    private static final int MAX_DEPTH = 2; // Maximum nested zip depth to extract
    private static final int BUFFER_SIZE = 4096;

    /**
     * Extracts files from a zip stream, including nested zips up to MAX_DEPTH.
     *
     * @param inputStream The input stream of the zip file.
     * @param outputDir   The base directory for extracted files.
     * @param currentDepth The current recursion depth.
     * @throws IOException if an I/O error occurs.
     */
    public void extract(final InputStream inputStream, final Path outputDir, final int currentDepth) throws IOException {
        if (currentDepth > MAX_DEPTH) {
            return;
        }

        // Do NOT close zis, as that would close the underlying stream
        // It will be closed by the caller
        final ZipInputStream zis = new ZipInputStream(inputStream);

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            final String entryName = entry.getName();
            final Path entryPath = outputDir.resolve(entryName);

            // Handle directories
            if (entry.isDirectory()) {
                this.fileSystemTargetAction.makeDirectoryOrThrow(entryPath);
                continue;
            }

            // Create a buffered stream for the entry's content
            // Allows us to mark and reset for the nested zip check
            final BufferedInputStream bufferedZis = new BufferedInputStream(zis, BUFFER_SIZE);

            // Check if it's a nested zip
            if (nestedUnpackNames.contains(entryName) && isNestedZip(bufferedZis)) {
                logger.info("Found nested archive: " + entryName + " at depth " + currentDepth);
                nestedUnpackNames.remove(entryName);
                // Recurse to handle the nested zip
                extract(bufferedZis, entryPath, currentDepth + 1);
            } else {
                // Extract regular file
                extractFile(bufferedZis, entryPath);
            }

            zis.closeEntry();
        }

    }

    /**
     * Extracts a single file entry from a buffered stream.
     *
     * @param bufferedIs The input stream of the file entry.
     * @param outputPath The path where the file should be saved.
     * @throws IOException if an I/O error occurs.
     */
    private void extractFile(final InputStream bufferedIs, final Path outputPath) throws IOException {
        // Ensure the parent directory exists
        this.fileSystemTargetAction.makeDirectoryOrThrow(outputPath.getParent());

        // Write the file content to disk
        try (final BufferedOutputStream bos = new BufferedOutputStream(this.fileSystemTargetAction.streamWrite(outputPath))) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = bufferedIs.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }
        }
    }

    /**
     * Checks if the given stream is a valid zip file without consuming it.
     *
     * @param is The input stream to check.
     * @return true if it's a nested zip, false otherwise.
     * @throws IOException if an I/O error occurs during mark/reset.
     */
    private boolean isNestedZip(final InputStream is) throws IOException {
        is.mark(BUFFER_SIZE);
        final ZipInputStream testZis = new ZipInputStream(is);
        boolean isZipped = false;
        try {
            if (testZis.getNextEntry() != null) {
                isZipped = true;
            }
        } catch (final IOException e) {
            // A ZipException or other IOException indicates it's not a valid zip
            isZipped = false;
        } finally {
            // Do NOT close testZis, as that would close the underlying stream
            is.reset();
        }
        return isZipped;
    }


}
