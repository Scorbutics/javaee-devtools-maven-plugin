package com.scorbutics.maven;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;

import com.scorbutics.maven.model.*;
import com.scorbutics.maven.service.filesystem.*;
import com.scorbutics.maven.service.filesystem.source.*;
import com.scorbutics.maven.service.filesystem.target.*;
import com.scorbutics.maven.service.packaging.*;
import com.scorbutics.maven.util.*;

@Mojo(name = "unit-deploy", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true, aggregator = true)
public class MojoUnitDeployPlugin extends BaseMojoDeploymentPlugin {

    @Parameter(property = "skipDeployment", defaultValue = "false")
    protected boolean skipDeployment;

	@Override
	protected Stream<ProjectComputer> getProjectComputers( final FileSystemSourceReader fileSystemSourceReader ) {
		return Stream.of(
				ProjectMavenReactorAnalyzer.builder().fileSystemSourceReader( fileSystemSourceReader).logger(getLog() ).build()
		);
	}

	@Override
	protected void actOnDeployments( final Path basePath, final Path target, final FileSystemSourceReader fileSystemSourceReader, final FileSystemTargetAction fileSystemTargetAction, final Collection<Deployment> allDeployments )
			throws
			MojoExecutionException {
		final RecursiveDirectoryWalker directoryWalker = new RecursiveDirectoryWalker( fileSystemSourceReader );
		SafeStream.of(SafeStream.of(allDeployments.stream())
						.tryAccept( deployment ->
								directoryWalker.copyFolderRecursive( fileSystemSourceReader, fileSystemTargetAction, deployment.getSource(), deployment.getTarget(), StandardCopyOption.REPLACE_EXISTING )
						)
						.failFast( e -> new MojoExecutionException("Error during unit-deploy operation: " + e.getMessage(), e ))
						// Force terminal operation to trigger processing
						.map(d -> d.getEnclosingTargetArchive(target))
						.distinct())
				.tryAccept( archivePath -> {
                    if (skipDeployment) {
                        getLog().info("Skipping redeployment of archive: " + archivePath);
                        return;
                    }
					getLog().info("Triggering redeployment of archive: " + archivePath);
					fileSystemTargetAction.touchFile(target.resolve(archivePath + ".dodeploy"));
				} )
				.failAfter(e -> new MojoExecutionException("Error during unit-deploy operation: " + e.getMessage(), e ));
	}


	@Override
	protected DeploymentType getDeploymentType() {
		return DeploymentType.UNIT_DEPLOYMENT;
	}

}