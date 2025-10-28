package com.scorbutics.maven.service;

import com.scorbutics.maven.model.Deployment;
import org.apache.maven.plugin.logging.Log;
import com.scorbutics.maven.exception.FileDeploymentException;
import com.scorbutics.maven.service.filesystem.target.FileSystemTargetAction;
import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;
import com.scorbutics.maven.service.filesystem.Unzipper;
import com.scorbutics.maven.util.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class FullDeployer {
    private final FileSystemTargetAction fileSystemTargetAction;
    private final FileSystemSourceReader fileSystemSourceReader;
    private final Unzipper unzipper;
    private final Log logger;

    public FullDeployer(final FileSystemSourceReader fileSystemSourceReader,
                        final FileSystemTargetAction fileSystemTargetAction,
                        final Unzipper unzipper,
                        final Log logger) {
        this.fileSystemTargetAction = fileSystemTargetAction;
        this.fileSystemSourceReader = fileSystemSourceReader;
        this.unzipper = unzipper;
        this.logger = logger;
    }

    public void deploy(final List<Deployment> autoDeployments, final Path basePath) throws FileDeploymentException {
		SafeStream.<Deployment, FileDeploymentException>of(autoDeployments.stream())
                .tryAccept(deployment ->
                        this.fileSystemSourceReader.readPattern(deployment.getSource().getParent(), deployment.getSource().getFileName().toString())
                                .forEach(path -> copyArtifact(path, basePath, deployment.getTarget().resolve(path.getFileName()), deployment.isUnpack()))
                )
				.failAfter();

    }

    private void copyArtifact(final Path path, final Path basePath, final Path targetPath, final boolean unpack) throws FileDeploymentException {
        if (this.fileSystemSourceReader.isDirectory(path)) {
            logger.warn("Artifact '" + path + "' is an directory - deployment skipped");
            return;
        }

        logger.info("Deploying '" + basePath.relativize(path) + "' " + (unpack ? "with unpacking" : "as is") + " to target '" + targetPath + "'");

        try {
            if (!unpack) {
                this.fileSystemTargetAction.feedStreamInFile(this.fileSystemSourceReader.streamRead(path), targetPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                this.fileSystemTargetAction.touchFile(targetPath.getParent().resolve(path.getFileName() + ".skipdeploy"));

                this.fileSystemTargetAction.deleteIfExists(targetPath.getParent().resolve(path.getFileName()));
                // unzip artifact....
                this.unzipper.unzipArtifact(path, targetPath);

                this.fileSystemTargetAction.touchFile(targetPath.getParent().resolve(path.getFileName() + ".dodeploy"));
                this.fileSystemTargetAction.deleteIfExists(targetPath.getParent().resolve(path.getFileName() + ".skipdeploy"));
            }
		} catch (final IOException e) {
            throw new FileDeploymentException("Error deploying artifact '" + path + "' to target '" + targetPath + "'", e);
        }
    }

}