package com.scorbutics.maven;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.scorbutics.maven.model.*;

import org.apache.maven.plugins.annotations.*;

import com.scorbutics.maven.service.*;
import com.scorbutics.maven.service.filesystem.local.*;
import com.scorbutics.maven.service.filesystem.target.*;
import com.scorbutics.maven.service.filesystem.watcher.*;
import com.scorbutics.maven.service.packaging.*;
import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;
import com.scorbutics.maven.util.*;

@Mojo(name = "watch-all", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class MojoWatchAllPlugin
		extends BaseMojoDeploymentPlugin {

	@Parameter(property = "verbose", defaultValue = "false")
	private boolean verbose;

	@Parameter(property = "show-progress", defaultValue = "true")
	private boolean showProgress;

	@Override
	protected Stream<ProjectComputer> getProjectComputers( final FileSystemSourceReader fileSystemSourceReader ) {
		return Stream.of(
				ProjectPluginsAnalyzer.builder().fileSystemSourceReader( fileSystemSourceReader ).logger( getLog() ).build(),
				ProjectMavenReactorAnalyzer.builder().fileSystemSourceReader( fileSystemSourceReader).logger(getLog() ).build()
		);
	}

	@Override
	protected DeploymentType getDeploymentType() {
		return DeploymentType.HOT_DEPLOYMENT;
	}


	@Override
	protected Predicate<Deployment> filterDeployment() {
		// exclude root deployments while watching
		return deployment -> deployment.getDepth() > 0 || !deployment.isComputed();
	}

	@Override
	protected void actOnDeployments( final Path basePath, final Path target, final FileSystemSourceReader fileSystemSourceReader, final FileSystemTargetAction fileSystemTargetAction, final Collection<Deployment> allDeployments ) {
		final EventWatcher watcher;
		try {
			watcher = new LocalFileSystemWatcher(getLog());
		} catch ( final IOException e ) {
			throw new RuntimeException( e );
		}

		final WatcherEventLogger eventLogger = WatcherEventLogger.builder().logger( getLog() ).sourceDir( basePath ).showProgress( showProgress ).verbose( verbose ).build();
		final RecursiveDirectoryWatcher directoryWatcher = new RecursiveDirectoryWatcher(watcher, fileSystemSourceReader, getLog());
		directoryWatcher.subscribe(eventLogger);

		final HotDeployer hotDeployer = new HotDeployer(directoryWatcher, fileSystemTargetAction, basePath, target, getLog());

		hotDeployer.registerAll(allDeployments);

		getLog().info("Watching...");
		for (;;) {
			try {
				hotDeployer.waitEvent();
			} catch (final InterruptedException e) {
				getLog().info("Hot Deployment interrupted: " + e.getMessage());
				break;
			}
		}
	}
}