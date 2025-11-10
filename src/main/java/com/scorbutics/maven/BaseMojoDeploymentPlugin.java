package com.scorbutics.maven;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.apache.maven.execution.*;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.*;

import com.scorbutics.maven.model.*;
import com.scorbutics.maven.service.*;
import com.scorbutics.maven.service.filesystem.local.*;
import com.scorbutics.maven.service.filesystem.source.*;
import com.scorbutics.maven.service.filesystem.target.*;
import com.scorbutics.maven.service.packaging.*;

import lombok.*;

public abstract class BaseMojoDeploymentPlugin
		extends AbstractMojo {

	@Parameter(property = "target")
	private String target;

	@Parameter(property = "deployments")
	private List<DeploymentRaw> deployments;

	@NonNull
	@Parameter(property = "structure")
	private StructureConfiguration structure = new StructureConfiguration();

	@Parameter(defaultValue = "${session}", required = true, readonly = true)
	private MavenSession session;

	/**
	 * Executes the Maven Mojo for hot deployment.
	 * <p>
	 * This method monitors the source directories for changes and automatically deploys
	 * updated files to the target exploded archive directory, enabling hot deployment.
	 * <p>
	 * It uses the {@code target} parameter to locate the exploded archive directory and the
	 * {@code deployments} parameter to determine which deployments to watch and update.
	 *
	 * @throws MojoExecutionException if the target directory does not exist, if deployments cannot be computed,
	 *                                or if an error occurs during hot deployment.
	 */
	public final void execute() throws MojoExecutionException {
		final Path basePath = session.getCurrentProject().getBasedir().toPath();
        final Path target = Paths.get(this.target);

		// TODO support other ways to replace files like Docker CP, FTP, SFTP, etc. for target filesystems
		final FileSystemTargetAction fileSystemTargetAction = new LocalFileSystemTargetAction();
		final FileSystemSourceReader fileSystemSourceReader = new LocalFileSystemSourceReader();

		if (!fileSystemTargetAction.exists(target)) {
			throw new MojoExecutionException("Unable to find any target exploded archive in path '" + target + "'");
		}

		final DeploymentType deploymentType = getDeploymentType();

		Collection<Deployment> allDeployments;
		try {
			final List<ProjectComputer> computers = getProjectComputers( fileSystemSourceReader ).collect( Collectors.toList() );
			if (structure.getAutoDiscovery().isEnabled()) {
				final int maxDeployedModulesDepthCheck =  structure.getAutoDiscovery().getMaxDeployedModulesDepthCheck();

				allDeployments = new DeploymentComputer( getLog(), computers, fileSystemTargetAction, basePath, target, maxDeployedModulesDepthCheck )
						.aggregateDeployments( session, deployments.stream().map(DeploymentRaw::toDeployment).collect(Collectors.toList()) , deploymentType.isForceTargetCreation(), deploymentType.isArchive() );
			} else {
				allDeployments = new ArrayList<>();
			}
		} catch ( final IOException e ) {
			throw new MojoExecutionException("Error computing deployments: " + e.getMessage(), e );
		}

		allDeployments = recursivelyFilterDeployments( allDeployments.stream(), filterDeployment() ).collect( Collectors.toList());

		if (allDeployments.isEmpty()) {
			getLog().info("No deployments defined");
			return;
		}
		DeploymentBanner.print( getLog(), allDeployments, basePath, target );

		actOnDeployments( basePath, target, fileSystemSourceReader, fileSystemTargetAction, allDeployments );
	}

	private Stream<Deployment> recursivelyFilterDeployments( final Stream<Deployment> allDeployments, final Predicate<Deployment> deploymentPredicate ) {
		return allDeployments
				.map( deployment -> {
					final Stream<Deployment> filteredChildren = recursivelyFilterDeployments( deployment.getChildren().values().stream().flatMap( List::stream ), deploymentPredicate );
					return deployment.toBuilder()
							.children( filteredChildren.collect(Collectors.groupingBy( Deployment::getSource )) )
							.build();
				} )
				// Post-order filtering: first filter children, then parent, because children may match even if parent does not
				.flatMap( deployment -> Stream.of( deployment.toBuilder().enabled( deploymentPredicate.test( deployment ) ).build() ) );
	}

	protected abstract Stream<ProjectComputer> getProjectComputers(FileSystemSourceReader fileSystemSourceReader);

	protected Predicate<Deployment> filterDeployment() {
		return deployment -> true;
	}

	protected abstract void actOnDeployments(
			Path basePath,
			Path target,
			FileSystemSourceReader fileSystemSourceReader,
			FileSystemTargetAction fileSystemTargetAction,
			Collection<Deployment> allDeployments
	)
			throws
			MojoExecutionException;

	protected abstract DeploymentType getDeploymentType();

}