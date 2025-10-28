package com.scorbutics.maven.model.packaging.computed;

import java.util.*;

/**
 * Pretty prints the module tree structure.
 */
public class ModuleTreePrettyPrinter implements ModuleTreePrinter {
	@Override
	public StringBuilder printStructure(final List<ComputedModule> structure) {
		final StringBuilder sb = new StringBuilder();
		for (final ComputedModule rootNode : structure) {
			sb.append( printSingleNode(rootNode) ).append( "\n" );
			printNode(sb, rootNode, 1);
		}
		return sb;
	}

	private void printNode(final StringBuilder sb, final ComputedModule node, final int depth) {
		final String indent = String.join("", Collections.nCopies(depth, "   "));

		for (final ComputedModule child : node.getChildren()) {
			sb.append( indent ).append( "- " ).append( printSingleNode(child) ).append( "\n" );
			if (!child.getChildren().isEmpty()) {
				printNode(sb, child, depth + 1);
			}
		}
	}

	private String printSingleNode(final ComputedModule node) {
		return node.getBaseDirectory() + " -> " +  node.getDeployedDirectory();
	}
}