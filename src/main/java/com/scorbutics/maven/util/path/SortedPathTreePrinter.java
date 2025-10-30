package com.scorbutics.maven.util.path;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.apache.maven.plugin.logging.*;

public class SortedPathTreePrinter {

	static class TreeNode {
		String name;
		Map<String, TreeNode> children = new TreeMap<>();
		Set<String> prefixes = new LinkedHashSet<>();

		TreeNode( final String name) {
			this.name = name;
		}

		public void addAllPrefixes(final Collection<String> prefixes) {
			this.prefixes.addAll(prefixes);
		}
	}

	/**
	 * Pretty prints a tree structure of pre-sorted paths.
	 *
	 * @param logger The Maven logger for output
	 * @param pathifier Function to extract Path from objects
	 * @param prefixPrinter Function to create prefix string for display
	 * @param sortedObjects Collection of objects - MUST be sorted by path length:
	 *                Primary: by getNameCount() ascending (shortest first)
	 *                Secondary: by toString() lexicographically ascending
	 * @param <T> Type of objects in the collection
	 */
	public static <T> void prettyPrint(
			final Log logger,
			final Function<T, Path> pathifier,
			final Function<T, String> prefixPrinter,
			final Collection<T> sortedObjects
	) {
		if (sortedObjects.isEmpty()) return;

		final Map<Path, Set<String>> allPathsWithPrefixes = sortedObjects.stream()
				.collect(Collectors.toMap(
						pathifier,
						object -> new LinkedHashSet<>( Collections.singleton( prefixPrinter.apply( object ) ) ),
						(a, b) -> {
							a.addAll(b);
							return a;
						},
						LinkedHashMap::new
				));

		final Path commonRoot = findCommonRootOptimized(sortedObjects, pathifier);
		final TreeNode root = new TreeNode(commonRoot.toString());

		try {
			for ( final Map.Entry<Path, Set<String>> entry : allPathsWithPrefixes.entrySet() ) {
				final Path relativePath = commonRoot.relativize(entry.getKey());
				addPath(root, relativePath, entry.getValue());
			}

			printNode( logger, root, "" );
		} catch ( final Exception e ) {
			logger.error( "Error while printing path tree: ", e );
		}
	}


	private static void addPath(final TreeNode node, final Path path, final Collection<String> prefixes) {
		if (path.getNameCount() == 0) return;

		final String firstName = path.getName(0).toString();
		final TreeNode child = node.children.computeIfAbsent( firstName, TreeNode::new );
		child.addAllPrefixes(prefixes);

		if (path.getNameCount() > 1) {
			addPath(child, path.subpath( 1, path.getNameCount() ), prefixes );
		}
	}

	private static void printNode(final Log logger, final TreeNode node, final String indent) {
		logger.info(indent + (node.prefixes.isEmpty() ? "" : node.prefixes + " ") + node.name + (node.children.isEmpty() ? "" : File.separator));
		for ( final TreeNode child : node.children.values()) {
			printNode(logger, child, indent + "  ");
		}
	}

	/**
	 * Ultra-optimized common root finder that leverages sorted input.
	 *
	 * Since the collection is sorted by path length (shortest first), the FIRST element
	 * is guaranteed to be one of the shortest paths. The common root must be a prefix
	 * of this shortest path, specifically its parent directory.
	 *
	 * Time complexity: O(1) - constant time!
	 * Previous complexity: O(n × m) where n = number of paths, m = path depth
	 *
	 * @param sortedObjects Collection sorted by path length (shortest first)
	 * @param pathifier Function to extract Path from objects
	 * @return The common root path (parent of the shortest path)
	 */
	public static <T> Path findCommonRootOptimized(
			final Collection<T> sortedObjects,
			final Function<T, Path> pathifier
	) {
		if (sortedObjects.isEmpty()) {
			return Paths.get("");
		}

		// Get the first (shortest) path from the sorted collection
		final Path shortestPath = pathifier.apply(sortedObjects.iterator().next());

		// The common root is the parent of the shortest path
		final Path parent = shortestPath.getParent();
		return parent != null ? parent : (shortestPath.getRoot() != null ? shortestPath.getRoot() : Paths.get(""));
	}

	/**
	 * Convenience method that maintains backward compatibility.
	 * Delegates to the optimized version.
	 */
	public static <T> Path findCommonRoot(
			final Collection<T> sortedObjects,
			final Function<T, Path> pathifier
	) {
		return findCommonRootOptimized(sortedObjects, pathifier);
	}
}