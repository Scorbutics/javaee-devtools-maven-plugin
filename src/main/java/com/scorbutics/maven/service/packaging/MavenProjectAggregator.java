package com.scorbutics.maven.service.packaging;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;

import lombok.RequiredArgsConstructor;

/**
 * Aggregates MavenProject instances from multiple sources:
 * - Current Maven reactor session projects
 * - External pom.xml files when configured
 */
@RequiredArgsConstructor
public class MavenProjectAggregator {

	private final ProjectBuilder projectBuilder;
	private final MavenSession session;
	private final Log logger;

	/**
	 * Aggregates Maven projects from the current session and optionally from an external base path.
	 *
	 * @param externalBasePath Optional external base path containing a pom.xml to load.
	 *                         If null, only session projects are returned.
	 * @return Combined list of all Maven projects
	 * @throws MojoExecutionException if external POM loading fails
	 */
	public List<MavenProject> aggregateProjects(final Path externalBasePath) throws MojoExecutionException {
		final List<MavenProject> allProjects = new ArrayList<>();

		// Always include session reactor projects
		final List<MavenProject> sessionProjects = session.getProjects();
		if (sessionProjects != null && !sessionProjects.isEmpty()) {
			logger.debug("Adding " + sessionProjects.size() + " project(s) from Maven reactor session");
			allProjects.addAll(sessionProjects);
		}

		// If external base path is configured, load projects from that location
		if (externalBasePath != null) {
			logger.info("External base path configured: " + externalBasePath.toAbsolutePath());

			final ExternalPomReader pomReader = new ExternalPomReader(projectBuilder, session, logger);
			final List<MavenProject> externalProjects = pomReader.readExternalPom(externalBasePath);

			logger.debug("Adding " + externalProjects.size() + " external project(s)");
			allProjects.addAll(externalProjects);
		}

		logger.debug("Total projects aggregated: " + allProjects.size());
		return allProjects;
	}
}
