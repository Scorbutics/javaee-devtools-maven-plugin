package com.scorbutics.maven.util.path;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.apache.maven.plugin.logging.*;

public class PathTreePrinter {

	static class TreeNode {
		String name;
		Map<String, TreeNode> children = new TreeMap<>();
		Set<String> prefixes = new HashSet<>();

		TreeNode( final String name) {
			this.name = name;
		}

		public void addAllPrefixes(final Collection<String> prefixes) {
			this.prefixes.addAll(prefixes);
		}
	}

	public static <T> void prettyPrint(final Log logger, final Function<T, Path> pathifier, final Function<T, String> prefixPrinter, final Collection<T> objects) {
		if (objects.isEmpty()) return;

		final Map<Path, Set<String>> allPathsWithPrefixes = objects.stream()
				.collect(Collectors.toMap(
						pathifier,
						object -> new HashSet<>( Collections.singleton( prefixPrinter.apply( object ) ) ),
						(a, b) -> {
							a.addAll(b);
							return a;
						}));

		final Path commonRoot = findCommonRoot(allPathsWithPrefixes.keySet());
		final TreeNode root = new TreeNode(commonRoot.toString());

		try {
			// Build the tree
			for ( final Map.Entry<Path, Set<String>> entry : allPathsWithPrefixes.entrySet() ) {
				final Path relativePath = commonRoot.relativize(entry.getKey());
				addPath(logger, root, relativePath, entry.getValue());
			}

			// Print the tree
			printNode( logger, root, "" );
		} catch ( final Exception e ) {
			logger.error( "Error while printing path tree: ", e );
		}
	}

	private static <T> void addPath(final Log logger, final TreeNode node, final Path path, final Collection<String> prefixes) {
		if (path.getNameCount() == 0) return;

		final String firstName = path.getName(0).toString();

		final TreeNode child = node.children.computeIfAbsent( firstName, TreeNode::new );
		child.addAllPrefixes(prefixes);

		if (path.getNameCount() > 1) {
			addPath(logger, child, path.subpath( 1, path.getNameCount() ), prefixes );
		}
	}

	private static void printNode(final Log logger, final TreeNode node, final String indent) {
		logger.info(indent + (node.prefixes.isEmpty() ? "" : node.prefixes + " ") + node.name + (node.children.isEmpty() ? "" : File.separator));
		for ( final TreeNode child : node.children.values()) {
			printNode(logger, child, indent + "  ");
		}
	}

	private static Path findCommonRoot( final Set<Path> paths) {
		final Path first = paths.iterator().next();
		Path commonRoot = first.getParent();

		while (commonRoot != null) {
			final Path root = commonRoot;
			if (paths.stream().allMatch(p -> p.startsWith(root))) {
				return root;
			}
			commonRoot = commonRoot.getParent();
		}

		return first.getRoot();
	}

	public static <T> Path findCommonRoot( final Collection<T> objects, final Function<T, Path> pathifier) {
		return findCommonRoot(objects.stream().map( pathifier ).collect( Collectors.toSet()));
	}
}