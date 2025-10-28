package com.scorbutics.maven.service;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.apache.maven.plugin.logging.*;

import com.scorbutics.maven.model.*;
import com.scorbutics.maven.model.packaging.*;

public class DeploymentBanner {

	public static void print(final Log logger, final Collection<Deployment> deployments, final Path basePath, final Path targetPath) {
		logger.info("Registering deployments:");
		printDeploymentsLines( deployments, basePath, targetPath, "  " ).forEach( logger::info );
	}

	private static Stream<String> printDeploymentsLines(final Collection<Deployment> deployments, final Path basePath, final Path targetPath, final String indent) {
		return deployments.stream()
				.sorted(Comparator.comparing(Deployment::isComputed).thenComparing( Deployment::getPackaging, Comparator.nullsFirst( Packaging::compareTo ) ))
				.flatMap(deployment -> {
					final Stream<String> children = printDeploymentsLines( deployment.getChildren().values().stream().flatMap( List::stream ).collect( Collectors.toList()), basePath, targetPath, indent + indent );
					return Stream.concat( Stream.of(indent + printLine(deployment, basePath, targetPath)), children );
				});
	}

	private static String printLine( final Deployment deployment, final Path basePath, final Path targetPath ) {
		final String content = basePath.relativize(deployment.getSource()) + " -> "
				+ (deployment.isUseSourceFilesystemOnly() ? basePath : targetPath).relativize(deployment.getTarget());
		return (deployment.isComputed() ? "(c)" : "[m]") + (deployment.getPackaging() != null ? " (" + deployment.getPackaging().getExtension() + ")" : "") + " - " + content + (deployment.isUseSourceFilesystemOnly() ? " [source FS only]" : "");
	}

}