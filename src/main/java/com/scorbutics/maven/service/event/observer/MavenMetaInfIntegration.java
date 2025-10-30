package com.scorbutics.maven.service.event.observer;

import java.nio.file.*;
import java.util.*;

import org.apache.maven.plugin.logging.*;

import com.scorbutics.maven.model.*;
import com.scorbutics.maven.service.event.watcher.compilation.*;
import com.scorbutics.maven.service.filesystem.*;
import com.scorbutics.maven.service.filesystem.source.*;
import com.scorbutics.maven.service.filesystem.target.*;
import com.scorbutics.maven.util.retry.*;

public class MavenMetaInfIntegration implements CompilationEventObserver {

	private final Collection<Deployment>    allDeployments;
	private final FileLockCheckerAndRetryer fileLockCheckerAndRetryer;
	private final FileSystemSourceReader fileSystemSourceReader;

	private final Log logger;
	private final Unzipper unzipper;

	public MavenMetaInfIntegration(
		final FileSystemSourceReader fileSystemSourceReader,
		final FileSystemTargetAction fileSystemTargetAction,
		final FileLockCheckerAndRetryer fileLockCheckerAndRetryer,
		final Collection<Deployment> allDeployments,
		final Log logger
	) {

		this.fileLockCheckerAndRetryer = fileLockCheckerAndRetryer;
		this.fileSystemSourceReader = fileSystemSourceReader;
		this.logger = logger;
		this.allDeployments = allDeployments;
		this.unzipper = new Unzipper(fileSystemSourceReader, fileSystemTargetAction, null, logger);
	}

	@Override
	public void onCleanEvent( final CompilationEvent event ) {
		logger.info( "[MavenMetaInfIntegration] Cleaning deployment base directory: " + event.getDeployment().getArchive() );
	}

	@Override
	public void onBuildFinishedEvent( final CompilationEvent event ) {
		final Path sourcePath = event.getDeployment().getArchive();
		final Path targetPath = event.getDeployment().getTarget();
		try {
			fileLockCheckerAndRetryer.doOnFileWithLockCheck(sourcePath, new StandardOpenOption[]{StandardOpenOption.READ}, false, () -> {
				logger.info( "[MavenMetaInfIntegration] Updating META-INF for deployment: from META-INF in archive " + sourcePath + " to " + targetPath );
				unzipper.unzipNested(
						Paths.get( "META-INF" ),
						null,
						sourcePath,
						targetPath
				);
			});


			// MANIFEST.MF integration from parent deployment if available
			final Optional<Deployment> parentDeployment = allDeployments.stream()
					.filter( deployment -> deployment.getDepth() < event.getDeployment().getDepth() )
					.filter( deployment -> deployment.getChildren().containsKey( event.getDeployment().getSource() ) )
					.filter( deployment -> deployment.getArchive() != null && this.fileSystemSourceReader.exists( deployment.getArchive() ) )
					.findAny();
			parentDeployment.ifPresent( deployment -> {
				logger.debug( "[MavenMetaInfIntegration] Found parent deployment for META-INF integration: " + deployment.getArchive() );
				final Path parentSourcePath = deployment.getArchive();
				fileLockCheckerAndRetryer.doOnFileWithLockCheck(parentSourcePath, new StandardOpenOption[]{StandardOpenOption.READ}, false, () -> {
					logger.debug( "[MavenMetaInfIntegration] Updating META-INF MANIFEST.MF using parent deployment: from META-INF in archive " + parentSourcePath + " to " + targetPath );
					unzipper.unzipNested(
							event.getDeployment().getArchive().getFileName().resolve( "META-INF" ).resolve( "MANIFEST.MF" ),
							event.getDeployment().getArchive().getFileName(),
							parentSourcePath,
							targetPath
					);
				});

			});
		} catch ( final RetryException e ) {
			logger.warn( "[MavenMetaInfIntegration] Error during META-INF integration for deployment: " + event.getDeployment().getArchive() + " -> " + targetPath + ": " + e.getMessage() );
		}
	}
}