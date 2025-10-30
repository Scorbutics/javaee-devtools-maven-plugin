package com.scorbutics.maven.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.apache.commons.lang3.tuple.*;
import org.apache.maven.execution.*;
import org.apache.maven.plugin.logging.*;
import org.apache.maven.project.*;

import com.scorbutics.maven.model.*;
import com.scorbutics.maven.model.packaging.computed.*;
import com.scorbutics.maven.service.filesystem.target.*;
import com.scorbutics.maven.service.packaging.*;

import lombok.*;

@RequiredArgsConstructor
public class DeploymentComputer {

	private final Log logger;
	private final List<ProjectComputer> computers;
	private final FileSystemTargetAction fileSystemTargetAction;
	private final Path basePath;
	private final Path targetPath;
	private final int maxDeployedModulesDepthCheck;

	private static final Deployment DUMB_DEPLOYMENT = Deployment.builder().build();

	private void validateManualDeployments(final List<Deployment> deployments) {
		// Normalize existing deployments folders to absolute paths
		// Validate deployments
		deployments.forEach(deployment -> {
			final DeploymentConfigurationError error = deployment.validate();
			if (error != DeploymentConfigurationError.NONE) {
				throw new IllegalArgumentException( "Invalid deployment configuration: " + deployment + " - Error: " + error.name() );
			}
			deployment.normalizePaths(basePath, targetPath);
		});
	}

	private Map<Path, List<Deployment>> buildDeploymentsTree(final boolean isArchive, final List<ComputedModule> modules) {
		return modules.stream()
				.map(module -> {

					// Build bottom-up approach: compute children before parents
					final Map<Path, List<Deployment>> children = buildDeploymentsTree(isArchive, module.getChildren());

					return Deployment.builder()
							.source(isArchive ? Paths.get(module.getSourceDirectory().toString() + "." + module.getExtension()) : module.getSourceDirectory())
							.computed(true)
							.depth(module.getDepth())
							.packaging(module.getPackaging())
							.children(children)
							.unpack(isArchive)
							.archive( module.getBuildOutputDirectory().resolve( module.getFinalNameWithExtension() )  )
							.base(module.getBaseDirectory())
							.target(module.getDeployedDirectory())
							.redeployOnChange(module.isRedeployOnChange())
							.build();
				})
				.collect( Collectors.groupingBy( Deployment::getSource, Collectors.mapping( Function.identity(), Collectors.toList() ) ) );
	}

	private Map<Path, List<Deployment>> buildComputedDeploymentsMap( final MavenSession session, final boolean mapOnTargetFileSystem, final boolean isArchive) {
		final Optional<ComputedProject> computedProject = new ProjectFileStructureAnalyzer(maxDeployedModulesDepthCheck, fileSystemTargetAction, computers, logger)
				.analyze( session, targetPath, mapOnTargetFileSystem);

		if (!computedProject.isPresent()) {
			logger.warn("Unable to deduce deployments from project structure.");
			return Collections.emptyMap();
		}

		return buildDeploymentsTree( isArchive, computedProject.get().getModules() );
	}

	public Collection<Deployment> aggregateDeployments(final MavenSession session, final List<Deployment> manualDeployments, final boolean mapOnTargetFileSystem, final boolean isArchive)
			throws
			IOException {

		// First compute deployments from project structure
		final Map<Path, List<Deployment>> computedDeployments = buildComputedDeploymentsMap(session, mapOnTargetFileSystem, isArchive);

		// Then if they exist, merge the manual deployments at the right place in the tree
		if (manualDeployments != null && !manualDeployments.isEmpty()) {
			validateManualDeployments( manualDeployments );
			return merge(manualDeployments, computedDeployments).values().stream().flatMap( List::stream ).collect( Collectors.toList());
		}

		return computedDeployments.values().stream().flatMap( List::stream ).collect( Collectors.toList());
	}

	private Optional<Deployment> locateDeploymentBySourcePathMatchingBasePath(final Path sourcePath, final Map<Path, List<Deployment>> deployments) {
		logger.debug( "Locating deployment for source path '" + sourcePath + "'" );

		// Search in children deployments recursively (bottom-up search)
		final Optional<Deployment> childFound = deployments.values().stream()
				.flatMap( List::stream )
				.map( Deployment::getChildren )
				.filter( children -> children != null && !children.isEmpty() )
				.map( children ->  locateDeploymentBySourcePathMatchingBasePath( sourcePath, children ))
				.findFirst()
				.orElse( Optional.empty() );

		if (childFound.isPresent()) {
			logger.debug( "Found starting with match for source path '" + sourcePath + "' -> '" + childFound.get().getBase() + "'" );
			return childFound;
		}

		// Check for deployments source path starting with the base path, if found, return it
		return deployments.values().stream()
				.flatMap( List::stream )
				.filter( deployment ->
						deployment.getBase() != null && sourcePath.startsWith(deployment.getBase())
				).findFirst();
	}

	private Map<Path, List<Deployment>> merge(final List<Deployment> manualDeployments, final Map<Path, List<Deployment>> computedDeployments) {
		final Map<Deployment, Set<Deployment>> computedToManualsDeployments = manualDeployments.stream()
				.map( deployment -> Pair.of(
							locateDeploymentBySourcePathMatchingBasePath( deployment.getSource(), computedDeployments )
									.orElse( DUMB_DEPLOYMENT ),
						deployment
					)
				)
				.peek( pair -> {
					if ( pair.getValue().getPackaging() == null ) {
						// Set manual deployment packaging to computed deployment packaging
						if ( pair.getKey() != DUMB_DEPLOYMENT ) {
							pair.getValue().setPackaging( pair.getKey().getPackaging() );
							logger.debug( "Setting packaging for manual deployment '" + pair.getValue() +
									"' to computed deployment packaging '" + pair.getKey().getPackaging() + "'" );
						} else {
							logger.warn( "Unable to set packaging for manual deployment '" + pair.getValue() +
									"' because no matching computed deployment could be found");
						}
					}
				})
				.collect( Collectors.groupingBy(Pair::getKey, Collectors.mapping( Pair::getValue, Collectors.toCollection(HashSet::new) )) );

		final Set<Deployment> rootsNonMappedManualDeployments = computedToManualsDeployments.remove( DUMB_DEPLOYMENT );
		// Only manual deployments without computed parent
		final Stream<Map.Entry<Path, Deployment>> manualRoots = rootsNonMappedManualDeployments == null ? Stream.empty() : rootsNonMappedManualDeployments.stream()
				.map( manualDeployment -> Pair.of(
								manualDeployment.getSource(),
								manualDeployment
						)
				);

		// Now process computed deployments that have manual deployments to merge
		final Stream<Map.Entry<Path, Deployment>> computedContainingManuals = mergeRecursively( computedToManualsDeployments, computedDeployments );

		return Stream.concat( manualRoots, computedContainingManuals )
				.collect( Collectors.groupingBy( Map.Entry::getKey ,
						Collectors.mapping( Map.Entry::getValue, Collectors.toList() ) ) );
	}

	private Stream<Map.Entry<Path, Deployment>> mergeRecursively( final Map<Deployment, Set<Deployment>> computedToManualsDeployments, final Map<Path, List<Deployment>> computedDeployments ) {
		return computedDeployments.entrySet().stream()
				.flatMap( entry -> entry.getValue().stream().map( deployment -> Pair.of( entry.getKey(), deployment ) ) )
				.map( pair -> {
					final Deployment deployment = pair.getValue();

					// Merge manual deployments to the existing computed children
					final Map<Path, List<Deployment>> childrenMap = mergeRecursively( computedToManualsDeployments, deployment.getChildren() )
							.collect( Collectors.groupingBy(
									Map.Entry::getKey,
									HashMap::new,
									Collectors.mapping(
										Map.Entry::getValue,
										Collectors.toList()
									)
							) );

					final Set<Deployment> manuals = computedToManualsDeployments.remove( deployment );

					// Check if there are manual deployments to merge under this computed deployment
					if ( manuals != null && !manuals.isEmpty() ) {
						logger.debug( "Merging " + manuals.size() + " manual deployment(s) into computed deployment '" + deployment.getSource() + "'" );
						manuals.forEach( manualDeployment ->
								childrenMap.computeIfAbsent( manualDeployment.getSource(), path -> new ArrayList<>() ).add( manualDeployment )
						);
					}

					// Return updated deployment with merged children
					return Pair.of(pair.getKey(), deployment.toBuilder()
							.children( childrenMap )
							.build() );
				});
	}
}