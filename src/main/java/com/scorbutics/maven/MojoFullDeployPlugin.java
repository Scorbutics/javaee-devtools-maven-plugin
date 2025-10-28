package com.scorbutics.maven;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.scorbutics.maven.model.Deployment;
import com.scorbutics.maven.service.*;
import com.scorbutics.maven.service.filesystem.Unzipper;
import com.scorbutics.maven.service.filesystem.local.*;
import com.scorbutics.maven.service.packaging.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;
import com.scorbutics.maven.service.filesystem.target.FileSystemTargetAction;
import com.scorbutics.maven.service.filesystem.target.LogFileSystemTargetActionDecorator;

@Mojo(name = "full-deploy", defaultPhase = LifecyclePhase.INSTALL, threadSafe = true, aggregator = true)
public class MojoFullDeployPlugin
		extends BaseMojoDeploymentPlugin {

	@Override
	protected Stream<ProjectComputer> getProjectComputers( final FileSystemSourceReader fileSystemSourceReader ) {
		return Stream.of(
				ProjectPluginsAnalyzer.builder().fileSystemSourceReader( fileSystemSourceReader ).logger( getLog() ).build(),
				ProjectMavenReactorAnalyzer.builder().fileSystemSourceReader( fileSystemSourceReader).logger(getLog() ).build()
		);
	}

	@Override
	protected void actOnDeployments( final Path basePath, final Path target, final FileSystemSourceReader fileSystemSourceReader, final FileSystemTargetAction fileSystemTargetAction, final Collection<Deployment> allDeployments )
			throws
			MojoExecutionException {

		final List<Deployment> rootDeployments = allDeployments.stream()
				.filter(deployment -> deployment.getDepth() == 0)
				.peek( deployment ->  {
					// set target to the main target for root deployments because those are archives
					deployment.setTarget( target );
				} )
				.collect(Collectors.toList());

		if (rootDeployments.isEmpty()) {
			getLog().info("No deployments defined");
			return;
		}

		final List<Deployment> nestedDeployments = allDeployments.stream()
				.flatMap( Deployment::flatten )
				.filter(deployment -> deployment.getDepth() == 1)
				.collect(Collectors.toList());

		final FullDeployer deployer = buildFullDeployer( nestedDeployments, target );


		deployer.deploy(rootDeployments, basePath);
	}

	@Override
	protected DeploymentType getDeploymentType() {
		return DeploymentType.FULL_DEPLOYMENT;
	}

	private FullDeployer buildFullDeployer(final List<Deployment> nestedDeployments, final Path target) {
        final FileSystemTargetAction fileSystemTargetAction = new LogFileSystemTargetActionDecorator(new LocalFileSystemTargetAction(), target, getLog());

        // Here we always read from the native local filesystem
        // Not sure if we should support anything else, like reading on a remote server...
        final FileSystemSourceReader fileSystemSourceReader = new LocalFileSystemSourceReader();

        final Collection<String> nestedUnpackedArtifacts = nestedDeployments.stream()
                .map(deployment -> deployment.getTarget().getFileName().toString())
                .collect(Collectors.toList());

		getLog().info("Nested unpacked artifacts: " + String.join(", ", nestedUnpackedArtifacts));

		return new FullDeployer(
				fileSystemSourceReader,
				fileSystemTargetAction,
				new Unzipper(fileSystemSourceReader, fileSystemTargetAction, nestedUnpackedArtifacts, getLog()),
				getLog()
		);
    }

}