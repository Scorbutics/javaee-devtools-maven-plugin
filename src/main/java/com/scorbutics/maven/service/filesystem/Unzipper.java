package com.scorbutics.maven.service.filesystem;

import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;
import com.scorbutics.maven.service.filesystem.target.FileSystemTargetAction;
import org.apache.maven.plugin.logging.Log;

import java.io.*;
import java.nio.file.*;
import java.util.*;
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
        this.nestedUnpackNames = nestedUnpackNames == null ? null : new HashSet<>(nestedUnpackNames);
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
            deepExtract(is, target, 0);
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
    public void deepExtract(final InputStream inputStream, final Path outputDir, final int currentDepth) throws IOException {
		deepWalk( inputStream, outputDir, currentDepth, new HashSet<>(), new ZipFileVisitor() {

			@Override
			public FileVisitResult onDirectoryFound( final Path directoryPath )
					throws
					IOException {
				fileSystemTargetAction.makeDirectoryOrThrow(directoryPath);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult onNestedZipFound( final InputStream nestedZipStream, final Path zipEntryPath )
					throws
					IOException {
				final boolean unpack = nestedUnpackNames != null && nestedUnpackNames.remove(zipEntryPath.getFileName().toString());
				if (unpack) {
					return FileVisitResult.CONTINUE;
				}

				// Extract the nested zip as a regular file
				extractFile(nestedZipStream, zipEntryPath);

				return FileVisitResult.SKIP_SUBTREE;
			}

			@Override
			public FileVisitResult onRegularFileFound( final InputStream fileStream, final Path filePath )
					throws
					IOException {
				// Extract regular file
				extractFile(fileStream, filePath);
				return FileVisitResult.CONTINUE;
			}
		});

    }

	interface ZipFileVisitor {
		FileVisitResult onDirectoryFound(Path directoryPath) throws IOException;

		FileVisitResult onNestedZipFound(InputStream nestedZipStream, Path zipEntryPath) throws IOException;

		FileVisitResult onRegularFileFound(InputStream fileStream, Path filePath) throws IOException;
	}

	private void deepWalk(final InputStream inputStream, final Path parent, final int currentDepth, final Collection<Path> skipped, final ZipFileVisitor visitor) throws IOException {
		if (currentDepth > MAX_DEPTH) {
			return;
		}

		// Do NOT close zis, as that would close the underlying stream
		// It will be closed by the caller
		final ZipInputStream zis = new ZipInputStream(inputStream);

		ZipEntry entry;
		while ( ( entry = zis.getNextEntry() ) != null ) {
			final String entryName = entry.getName();
			final Path entryPath = parent == null ? Paths.get( entryName ) : parent.resolve( entryName );

			if ( skipped.stream().anyMatch( entryPath::startsWith ) ) {
				zis.closeEntry();
				continue;
			}

			// Handle directories
			if ( entry.isDirectory() ) {
				final FileVisitResult result = visitor.onDirectoryFound( entryPath );
				switch ( result ) {
					case TERMINATE:
						zis.closeEntry();
						return;

					case SKIP_SIBLINGS:
					case SKIP_SUBTREE:
						skipped.add( entryPath );
					case CONTINUE:
						zis.closeEntry();
						continue;
				}
			}

			// Create a buffered stream for the entry's content
			// Allows us to mark and reset for the nested zip check
			final BufferedInputStream bufferedZis = new BufferedInputStream( zis, BUFFER_SIZE );

			// Check if it's a nested zip
			if ( isNestedZip( bufferedZis ) ) {
				final FileVisitResult result = visitor.onNestedZipFound( bufferedZis, entryPath );
				switch ( result ) {
					case TERMINATE:
						zis.closeEntry();
						return;

					case SKIP_SIBLINGS:
					case SKIP_SUBTREE:
						skipped.add( entryPath );
						zis.closeEntry();
						continue;
					case CONTINUE:
						// Recurse to handle the nested zip
						deepWalk( bufferedZis, entryPath, currentDepth + 1, skipped, visitor );
						break;
				}
			} else {
				// Extract regular file
				final FileVisitResult result = visitor.onRegularFileFound( bufferedZis, entryPath );
				switch ( result ) {
					case TERMINATE:
						zis.closeEntry();
						return;

					case SKIP_SIBLINGS:
					case SKIP_SUBTREE:
						skipped.add( entryPath );
					case CONTINUE:
						break;
				}
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

	/**
	 * Unzip a folder contained in the archive
	 */
	public void unzipNested( final Path subFolderOrFilePath, final Path relativizeOutputDir, final Path fileZip, final Path outputDir ) throws IOException {

		try (final InputStream is = this.fileSystemSourceReader.streamRead(fileZip)) {
			deepWalk( is, null, 0, new HashSet<>(), new ZipFileVisitor() {

				@Override
				public FileVisitResult onDirectoryFound( final Path directoryPath )
						throws
						IOException {
					if ( directoryPath.startsWith( subFolderOrFilePath.toString() + "/" ) ) {
						final Path targetPath = relativizeOutputDir == null ? outputDir.resolve( directoryPath ) : relativizeOutputDir.relativize( outputDir.resolve( directoryPath ) );
						fileSystemTargetAction.makeDirectoryOrThrow( targetPath );
						return FileVisitResult.CONTINUE;
					}
					return FileVisitResult.SKIP_SUBTREE;
				}

				@Override
				public FileVisitResult onNestedZipFound( final InputStream nestedZipStream, final Path zipEntryPath )
						throws
						IOException {
					final boolean included = findCommonAncestor( zipEntryPath, subFolderOrFilePath ) != null;
					return included ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
				}

				@Override
				public FileVisitResult onRegularFileFound( final InputStream fileStream, final Path filePath )
						throws
						IOException {

					if (filePath.equals( subFolderOrFilePath ) ) {
						// single file extraction
						final Path targetPath = outputDir.resolve(relativizeOutputDir == null ? filePath : relativizeOutputDir.relativize(filePath) );
						extractFile(fileStream, targetPath);
						return FileVisitResult.TERMINATE;
					}
					return FileVisitResult.CONTINUE;
				}
			} );
		}
	}

	private static Path findCommonAncestor( final Path path1, final Path path2) {
		final Path normalizedPath1 = path1.normalize();
		final Path normalizedPath2 = path2.normalize();

		Path common = null;
		for (int i = 0; i < normalizedPath1.getNameCount() && i < normalizedPath2.getNameCount(); i++) {
			if (normalizedPath1.getName(i).equals(normalizedPath2.getName(i))) {
				if (common == null) {
					common = normalizedPath1.getRoot();
				}
				common = common == null ? normalizedPath1.getName(i) : common.resolve(normalizedPath1.getName(i));
			} else {
				break;
			}
		}
		return common;
	}
}