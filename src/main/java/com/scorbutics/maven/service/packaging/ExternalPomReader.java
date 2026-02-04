package com.scorbutics.maven.service.packaging;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;

import lombok.RequiredArgsConstructor;

/**
 * Reads external pom.xml files and builds MavenProject instances.
 * Supports multi-module POMs by recursively loading all child modules.
 */
@RequiredArgsConstructor
public class ExternalPomReader {

	private final ProjectBuilder projectBuilder;
	private final MavenSession session;
	private final Log logger;

	/**
	 * Reads a pom.xml file from the given path and builds MavenProject instances.
	 * If the POM is a multi-module project, all child modules are recursively loaded.
	 *
	 * @param pomPath Path to the directory containing pom.xml
	 * @return List of MavenProject instances (parent and all modules)
	 * @throws MojoExecutionException if the pom.xml cannot be read or parsed
	 */
	public List<MavenProject> readExternalPom(final Path pomPath) throws MojoExecutionException {
		final File pomFile = pomPath.resolve("pom.xml").toFile();

		if (!pomFile.exists()) {
			throw new MojoExecutionException(
					"No pom.xml found in external base directory: " + pomPath.toAbsolutePath()
			);
		}

		logger.debug("Reading external pom.xml from: " + pomFile.getAbsolutePath());

		try {
			final ProjectBuildingRequest buildingRequest = createProjectBuildingRequest();
			final ProjectBuildingResult result = projectBuilder.build(pomFile, buildingRequest);
			final MavenProject rootProject = result.getProject();

			final List<MavenProject> allProjects = new ArrayList<>();
			collectAllProjects(rootProject, allProjects, buildingRequest);

			logger.info("Loaded " + allProjects.size() + " project(s) from external POM");
			return allProjects;

		} catch (final ProjectBuildingException e) {
			throw new MojoExecutionException(
					"Failed to build Maven project from external pom.xml: " + pomFile.getAbsolutePath()
							+ " - " + e.getMessage(),
					e
			);
		}
	}

	/**
	 * Recursively collects all projects including the root and all child modules.
	 *
	 * @param project The current project to process
	 * @param allProjects The accumulator list for all projects
	 * @param buildingRequest The project building request configuration
	 * @throws ProjectBuildingException if a child module cannot be built
	 */
	private void collectAllProjects(
			final MavenProject project,
			final List<MavenProject> allProjects,
			final ProjectBuildingRequest buildingRequest
	) throws ProjectBuildingException {

		allProjects.add(project);

		// Process child modules recursively
		final List<String> modules = project.getModules();
		if (modules != null && !modules.isEmpty()) {
			logger.debug("Project " + project.getArtifactId() + " has " + modules.size() + " module(s)");

			for (final String moduleName : modules) {
				final File moduleDir = new File(project.getBasedir(), moduleName);
				final File modulePomFile = new File(moduleDir, "pom.xml");

				if (modulePomFile.exists()) {
					logger.debug("Building child module: " + moduleName);
					final ProjectBuildingResult moduleResult = projectBuilder.build(modulePomFile, buildingRequest);
					final MavenProject moduleProject = moduleResult.getProject();

					// Recursively collect all nested modules
					collectAllProjects(moduleProject, allProjects, buildingRequest);
				} else {
					logger.warn("Module pom.xml not found: " + modulePomFile.getAbsolutePath());
				}
			}
		}
	}

	/**
	 * Creates a ProjectBuildingRequest based on the current Maven session.
	 * Dependencies are not resolved for performance reasons, as we only need POM metadata.
	 *
	 * @return Configured ProjectBuildingRequest
	 */
	private ProjectBuildingRequest createProjectBuildingRequest() {
		final ProjectBuildingRequest buildingRequest = new org.apache.maven.project.DefaultProjectBuildingRequest(
				session.getProjectBuildingRequest()
		);

		// Don't resolve dependencies - we only need POM structure and metadata
		buildingRequest.setResolveDependencies(false);
		buildingRequest.setProcessPlugins(false);

		return buildingRequest;
	}
}
