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

public abstract class BaseMojoDeploymentPlugin
		extends AbstractMojo {

	@Parameter(property = "target")
	private Path target;

	@Parameter(property = "deployments")
	private List<Deployment> deployments;

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

		Collection<Deployment> allDeployments;

		// TODO support other ways to replace files like Docker CP, FTP, SFTP, etc. for target filesystems
		final FileSystemTargetAction fileSystemTargetAction = new LocalFileSystemTargetAction();
		final FileSystemSourceReader fileSystemSourceReader = new LocalFileSystemSourceReader();

		if (!fileSystemTargetAction.exists(target)) {
			throw new MojoExecutionException("Unable to find any target exploded archive in path '" + target + "'");
		}

		final DeploymentType deploymentType = getDeploymentType();

		try {
			final List<ProjectComputer> computers = getProjectComputers( fileSystemSourceReader ).collect( Collectors.toList() );

			allDeployments = new DeploymentComputer(getLog(), computers, fileSystemTargetAction, basePath, target)
					.aggregateDeployments( session, deployments, deploymentType.isForceTargetCreation(), deploymentType.isArchive() );
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
				.flatMap( deployment -> {
					if ( deploymentPredicate.test( deployment ) ) {
						return Stream.of( deployment );
					} else {
						return deployment.getChildren().values().stream().flatMap( List::stream );
					}
				} );
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