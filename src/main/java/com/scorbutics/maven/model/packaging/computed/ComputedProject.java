package com.scorbutics.maven.model.packaging.computed;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.apache.maven.plugin.logging.*;
import org.apache.maven.project.*;

import com.scorbutics.maven.model.packaging.*;
import com.scorbutics.maven.service.filesystem.source.*;

import lombok.*;


@Builder(toBuilder = true)
@Value
public class ComputedProject {

	public StringBuilder print(final ModuleTreePrinter moduleTreePrinter) {
		return moduleTreePrinter.printStructure(modules);
	}

	/**
	 * Represents a node in the artifact structure tree
	 */
	@AllArgsConstructor
	@EqualsAndHashCode(onlyExplicitlyIncluded = true)
	@ToString(onlyExplicitlyIncluded = true)
	@Value
	static class ArtifactNode {
		@EqualsAndHashCode.Include
		@ToString.Include
		XMLModule module;
		List<ArtifactNode> children = new ArrayList<>();
		// true for leaf modules, false for plugins
		boolean isLeaf;

		public void addChild( final ArtifactNode child) {
			children.add(child);
		}

		private static List<ComputedModule> buildChildrenModules(final ArtifactNode node, final Map<MavenProjectKey, MavenProject> projectMap, final int depth, final FileSystemSourceReader fileSystemSourceReader, final Log logger) {
			return node.getChildren().stream()
					.map( childNode -> childNode.toComputedModule(projectMap, depth, fileSystemSourceReader, logger) )
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
		}

		public ComputedModule toComputedModule(final Map<MavenProjectKey, MavenProject> projectMap, final int depth, final FileSystemSourceReader fileSystemSourceReader, final Log logger) {
			final MavenProject project = projectMap.get(MavenProjectKey.fromModule(module));
			if (project == null) {
				logger.warn("No MavenProject found for module: " + module.getIdentifier() + ", skipping.");
				return null;
			}
			final List<ComputedModule> children = buildChildrenModules(this, projectMap, depth + 1, fileSystemSourceReader, logger);
			return module.buildComputedModule(children, depth, project, fileSystemSourceReader, logger).orElse(null);
		}
	}

	@Value
	static class MavenProjectKey {
		String groupId;
		String artifactId;

		public static MavenProjectKey fromProject(final MavenProject project) {
			return new MavenProjectKey(project.getGroupId(), project.getArtifactId());
		}

		public static MavenProjectKey fromModule( final XMLModule module ) {
			return new MavenProjectKey(module.getGroupId(), module.getArtifactId());
		}
	}

	List<ComputedModule> modules;

	public List<ComputedModule> getModulesFlattened() {
		return modules.stream()
				.flatMap(ComputedModule::flattenedStream)
				.collect( Collectors.toList() );
	}

	public static ComputedProject fromXMLPluginConfiguration(final List<XMLContainerPluginConfiguration> plugins, final List<MavenProject> allProjects, final FileSystemSourceReader fileSystemSourceReader, final Log logger) {
		final Map<MavenProjectKey, MavenProject> projectMap = allProjects.stream()
				.collect(Collectors.toMap(MavenProjectKey::fromProject, Function.identity()));

		final Map<XMLModule, List<XMLModule>> pluginMappings = plugins.stream()
				.collect(Collectors.toMap( XMLContainerPluginConfiguration::getRootModule, XMLContainerPluginConfiguration::getModules));

		final List<ArtifactNode> structure = buildArtifactStructure(pluginMappings);
		final List<ComputedModule> computedModules = structure.stream()
				.map( node -> node.toComputedModule(projectMap, 0, fileSystemSourceReader, logger) )
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		return ComputedProject.builder()
				.modules( computedModules )
				.build();
	}

	private static List<ArtifactNode> buildArtifactStructure(final Map<XMLModule, List<XMLModule>> pluginMappings) {
		// Identify which items are leaf modules (not plugins)
		final Set<XMLModule> allModules = pluginMappings.values().stream()
				.flatMap(List::stream)
				.filter(dep -> !pluginMappings.containsKey(dep))
				.collect( Collectors.toSet());

		return pluginMappings.keySet()
				.stream()
				.map(plugin -> buildArtifactTree(pluginMappings, allModules, plugin, new HashSet<>()))
				.collect(Collectors.toList());
	}

	/**
	 * Recursively builds the tree for a given plugin/module
	 *
	 * @param current
	 * 		The current plugin/module being processed
	 * @param visited
	 * 		Set of visited nodes to detect cycles
	 * @return The root node of the subtree
	 */
	private static ArtifactNode buildArtifactTree(final Map<XMLModule, List<XMLModule>> pluginMappings, final Set<XMLModule> allModules, final XMLModule current, final Set<XMLModule> visited) {
		// Create a new visited set for this path to allow same node in different branches
		final Set<XMLModule> currentPath = new HashSet<>(visited);

		// Check for cycles
		if (currentPath.contains(current)) {
			// Cycle detected, return a leaf node to break the cycle
			return new ArtifactNode(current.toCycleBreakingModule(), true);
		}

		currentPath.add(current);

		// Check if this is a leaf module
		final boolean isLeaf = allModules.contains(current);
		final ArtifactNode node = new ArtifactNode(current, isLeaf);

		// If it's a plugin, recursively expand its dependencies
		if (pluginMappings.containsKey(current)) {
			final List<XMLModule> dependencies = pluginMappings.get(current);
			for (final XMLModule dep : dependencies) {
				node.addChild(buildArtifactTree(pluginMappings, allModules, dep, currentPath));
			}
		}

		return node;
	}

	public ComputedProject rebuildEachModule(final Function<ComputedModule, ComputedModule.ComputedModuleBuilder> mapper) {
		return toBuilder().modules( modules.stream().map( module -> module.rebuildEachModule(mapper)).collect( Collectors.toList())).build();
	}

}