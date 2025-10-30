package com.scorbutics.maven.service.packaging;

import com.scorbutics.maven.model.packaging.*;
import com.scorbutics.maven.model.packaging.computed.*;
import com.scorbutics.maven.service.filesystem.RecursiveDirectoryWalker;
import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;
import com.scorbutics.maven.service.filesystem.target.FileSystemTargetAction;
import com.scorbutics.maven.util.*;

import org.apache.maven.execution.*;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProjectFileStructureAnalyzer {

	private static final int MAX_DEPTH_RECURSIVE_CHECK_ON_DEPLOYED_MODULES = 20;
	private static final int MIN_DEPTH_RECURSIVE_CHECK_ON_DEPLOYED_MODULES = 2;

    private final Collection<ProjectComputer> computers;
    private final RecursiveDirectoryWalker recursiveDirectoryWalker;
    private final Log logger;
	private final int maxDeployedModulesDepthCheck;

    public ProjectFileStructureAnalyzer( final int maxDeployedModulesDepthCheck, final FileSystemTargetAction fileSystemTargetAction, final Collection<ProjectComputer> computers, final Log logger) {
        this.recursiveDirectoryWalker = new RecursiveDirectoryWalker(fileSystemTargetAction);
        this.computers = computers;
        this.logger = logger;
		this.maxDeployedModulesDepthCheck = Math.min( Math.max( maxDeployedModulesDepthCheck, MIN_DEPTH_RECURSIVE_CHECK_ON_DEPLOYED_MODULES), MAX_DEPTH_RECURSIVE_CHECK_ON_DEPLOYED_MODULES ) ;
    }

    public Optional<ComputedProject> analyze(final MavenSession session, final Path target, final boolean shouldMapOnTargetFilesystem) {
        final Optional<ComputedProject> result = computeUsingMavenProject(session)
                .map(config -> shouldMapOnTargetFilesystem ? rebuildHierarchyUsingTargetDeploymentsFilesystem(config, target) : createHierarchyDirectory(config, target));

		if (result.isPresent() && logger.isDebugEnabled()) {
			final StringBuilder printedProject = result.get().print(new ModuleTreePrettyPrinter());
			logger.debug("Auto-computed project structure:\n" + printedProject.toString());
		}
		return result;
    }

    private ComputedProject createHierarchyDirectory(final ComputedProject project, final Path target) {
		return project.rebuildEachModule(module -> module.toBuilder().deployedDirectory(target.resolve(module.getFinalNameWithExtension())));
    }

    private List<ComputedModule> recursiveCheckDeployedModules(final List<ComputedModule> modulesToMatch, final Path currentPath, final int depth) {

		if ( depth > maxDeployedModulesDepthCheck ) {
			logger.debug( "Maximum depth reached when checking deployed modules at path: " + currentPath );
			return new ArrayList<>();
		}

		// Check deployed directories against configured modules
		final List<Path> pathsAtCurrentDepthToMatch = this.recursiveDirectoryWalker.readRecursive( currentPath.resolve( "." ), 2, null )
				// Exclude itself
				.filter( path -> !path.getFileName().toString().equals( "." ) )
				.map( Path::normalize ).distinct().collect( Collectors.toList() );

		logger.debug( "Matching deployed directories: \n" +
				pathsAtCurrentDepthToMatch.stream()
						.map( path -> path.getFileName().toString() )
						.collect( Collectors.joining( "\n" ) )
				+ "\n against configured modules: \n" +
				modulesToMatch.stream()
						.map( ComputedModule::getFinalNameWithExtension )
						.collect( Collectors.joining( "\n" ) )
		);

		// Find the best matching module for this directory
		// If multiple modules match, we take the one with the highest score
		// If no module matches, we ignore this directory
		final Map<ComputedModule, Path> localMatches = FuzzyListMatcher.findAndRemoveMatches(
				modulesToMatch,
				pathsAtCurrentDepthToMatch,
				ComputedModule::getFinalNameWithExtension,
				path -> path.getFileName().toString(),
				0.8
		);

		if (logger.isDebugEnabled()) {
			final String foundLog = localMatches.entrySet().stream()
					.map( entry -> " - Directory '" + entry.getValue().getFileName().toString() + "' matched to module '" + entry.getKey().getFinalName() + "'"
							+ ( depth != entry.getKey().getDepth() ? " (configured depth: " + entry.getKey().getDepth() + ", found at depth: " + depth + ")" : "" ) )
					.collect( Collectors.joining( "\n" ) );
			if ( !foundLog.isEmpty() ) {
				logger.debug( "Found deployed modules at level " + depth + ":\n" + foundLog );
			}
		}

		// If no local matches found, go deeper in the tree until we find matches
		if (localMatches.isEmpty() && !pathsAtCurrentDepthToMatch.isEmpty()) {
			return pathsAtCurrentDepthToMatch.stream().map( path ->
					recursiveCheckDeployedModules(modulesToMatch, path, depth + 1)
			)
			.filter( list -> !list.isEmpty() )
			.findFirst()
			.orElse(new ArrayList<>());
		}

		// Rebuild the tree bottom-up using the local deployments matches found at this level
        return localMatches.entrySet().stream().map(entry -> {
            final Path path = entry.getValue();
            final ComputedModule module = entry.getKey();

			// Go bottom-up to find children deployed directories
			final List<ComputedModule> children = !modulesToMatch.isEmpty() ?
				recursiveCheckDeployedModules(modulesToMatch, path, depth + 1) : new ArrayList<>();

            // Update with the actual deployed directory, depth and children
            return module.toBuilder().deployedDirectory(path).depth(depth).children(children).build();
        })
		.collect(Collectors.toList());
        // Ignore remaining directories at this level in pathsAtCurrentDepthToMatch
    }

    private List<ComputedModule> rebuildUsingDeployedModules(final List<ComputedModule> modulesToMatch, final Path target) {
        final List<ComputedModule> undeployedModules = new ArrayList<>(modulesToMatch);

        // Ensure configured modules have a directory by filling deployed modules
		final List<ComputedModule> firstLevelDeployedModules = recursiveCheckDeployedModules(undeployedModules, target, 0);

        // Check configured modules against deployed modules for logging purposes
        undeployedModules
                .forEach(module -> logger.warn("Module '" + module.getSourceDirectory() + "' named '" + module.getFinalName() + "' is not deployed. Ignored."));
        return firstLevelDeployedModules;
    }

    public ComputedProject rebuildHierarchyUsingTargetDeploymentsFilesystem(final ComputedProject project, final Path target) {
		// Voluntarily rebuild the project structure using deployed modules found on target filesystem matching flattened configured modules
        return ComputedProject.builder().modules(rebuildUsingDeployedModules(project.getModulesFlattened(), target)).build();
    }

    private Optional<ComputedProject> computeUsingMavenProject(final MavenSession session) {
		return computers.stream()
				.map( computer -> computer.compute( session.getProjects() ) )
				.filter( Optional::isPresent )
				.map( Optional::get )
				.findFirst();
    }



}