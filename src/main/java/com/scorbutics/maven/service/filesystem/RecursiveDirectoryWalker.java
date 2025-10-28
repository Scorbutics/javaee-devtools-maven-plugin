package com.scorbutics.maven.service.filesystem;

import com.scorbutics.maven.exception.FileWatcherException;
import com.scorbutics.maven.service.filesystem.source.*;
import com.scorbutics.maven.service.filesystem.target.*;
import com.scorbutics.maven.util.*;

import lombok.AllArgsConstructor;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.*;

import org.apache.maven.plugin.logging.*;

@AllArgsConstructor
public class RecursiveDirectoryWalker {

    private FileWalker walker;

    public Stream<Path> readRecursive(final Path sourcePath, final int maxDepth, final Set<Path> excludedSubtrees) throws FileWatcherException {
        // Ensure any exception during the directory traversal throws a runtime exception
        return safeReadRecursive(sourcePath, maxDepth, excludedSubtrees)
				.failAfter(FileWatcherException::new);
    }

    /**
     * Helper method to read all subdirectories in a root directory.
     *
     * @param root
     */
	public SafeStream<Path, IOException> safeReadRecursive(final Path root, final int maxDepth, final Set<Path> excludedSubtrees) {
		final List<SafeStream.Try<Path, IOException>> results = new ArrayList<>();

		try {
			walker.walkTree(root, maxDepth, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
					// As soon as we find an excluded root, skip all its subtree
					if (excludedSubtrees != null && excludedSubtrees.contains(dir)) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					results.add( SafeStream.Try.success(dir));
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
					results.add( SafeStream.Try.failure(exc));
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (final IOException e) {
			// If walkTree itself fails, return a stream with just that error
			return new SafeStream<>(Stream.of( SafeStream.Try.failure(e)));
		}

		return new SafeStream<>(results.stream());
	}

	public void walk(final Log logger, final Path root, final int maxDepth, final Set<Path> excludedSubtrees, final FileVisitor<? super Path> visitor) {
		try {
			walker.walkTree( root, maxDepth, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory( final Path dir, final BasicFileAttributes attrs ) {
					// As soon as we find an excluded root, skip all its subtree
					if ( excludedSubtrees != null && excludedSubtrees.contains( dir ) ) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					try {
						return visitor.preVisitDirectory( dir, attrs );
					} catch ( final IOException e ) {
						logger.error("Error during directory walk: " + e.getMessage());
						return FileVisitResult.CONTINUE;
					}
				}

				@Override
				public FileVisitResult visitFile( final Path file, final BasicFileAttributes attrs ) {
					try {
						return visitor.visitFile( file, attrs );
					} catch ( final IOException e ) {
						logger.error("Error during visiting file: " + e.getMessage());
						return FileVisitResult.CONTINUE;
					}
				}

				@Override
				public FileVisitResult visitFileFailed( final Path file, final IOException exc ) {
					try {
						return visitor.visitFileFailed( file, exc );
					} catch ( final IOException e ) {
						//logger.error("Error during failing directory walk: " + e.getMessage(), e);
						return FileVisitResult.CONTINUE;
					}
				}
			} );
		} catch ( final Exception e ) {
			logger.error( "Error during directory walk: " + e.getMessage());
		}

	}

	public void copyFolderRecursive(final FileSystemSourceReader sourceAction, final FileSystemTargetAction targetAction, final Path source, final Path target, final StandardCopyOption... options)
			throws IOException {
		walker.walkTree(source, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory( final Path dir, final BasicFileAttributes attrs)
					throws IOException {
				targetAction.makeDirectoryOrThrow(target.resolve(source.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile( final Path file, final BasicFileAttributes attrs)
					throws IOException {
				targetAction.feedStreamInFile( sourceAction.streamRead(file), target.resolve(source.relativize(file).toString()), options);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}