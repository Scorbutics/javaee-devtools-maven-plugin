package com.scorbutics.maven.service.packaging;

import java.util.*;
import java.util.stream.*;

import org.apache.maven.model.*;
import org.apache.maven.plugin.logging.*;
import org.apache.maven.project.*;

import com.scorbutics.maven.model.packaging.*;
import com.scorbutics.maven.model.packaging.computed.*;
import com.scorbutics.maven.service.filesystem.source.*;

import lombok.*;

@Builder
public class ProjectPluginsAnalyzer implements ProjectComputer {

	private final FileSystemSourceReader fileSystemSourceReader;
	private final Log                    logger;

	/**
	 * Compute the project structure using packaging plugin configurations
	 * @param allProjects all Maven projects in the reactor
	 * @return an optional computed project if all required plugin configurations are found and valid, empty otherwise
	 */
	public Optional<ComputedProject> compute(final List<MavenProject> allProjects) {
		final List<XMLContainerPluginConfiguration> pluginConfigurations = allProjects.stream()
				// Filter out projects that do not use packaging plugins
				.filter(project -> !Packaging.excluded(project.getPackaging()))
				.map(project -> {
							final Packaging packaging = Packaging.fromPackaging(project.getPackaging());
							// Ignore non-plugin-based configured packagings
							if (!packaging.isHasPlugingConfigurationSubmodules()) {
								return Optional.<XMLContainerPluginConfiguration> empty();
							}
							return project.getBuildPlugins().stream()
									.filter(p -> p.getArtifactId().equals(packaging.getPluginId()))
									.findFirst()
									.flatMap( plugin -> {
										try {
											return Optional.of( XMLContainerPluginConfiguration.fromPluginAndProject(packaging.getPluginConfigurationClass(), plugin, project, logger));
										} catch (final RuntimeException e) {
											logger.warn("Error parsing " + packaging.getPluginId() + " configuration for project " + project.getArtifactId() + ". Using default configuration.", e);
											return Optional.empty();
										}
									});
						}
				)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect( Collectors.toList());

		if (pluginConfigurations.size() < allProjects.stream()
				.filter(project -> !Packaging.excluded(project.getPackaging()))
				.map(project -> Packaging.fromPackaging(project.getPackaging()))
				.filter(Packaging::isHasPlugingConfigurationSubmodules)
				.count() ) {
			logger.warn("Not all projects using packaging plugins have a valid plugin configuration, skipping plugin-based project structure analysis.");
			return Optional.empty();
		}

		return Optional.of(ComputedProject.fromXMLPluginConfiguration(pluginConfigurations, allProjects, fileSystemSourceReader, logger));
	}
}