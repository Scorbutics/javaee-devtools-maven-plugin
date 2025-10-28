package com.scorbutics.maven.service.packaging;

import java.util.*;
import java.util.stream.*;

import org.apache.maven.plugin.logging.*;
import org.apache.maven.project.*;

import com.scorbutics.maven.model.packaging.*;
import com.scorbutics.maven.model.packaging.computed.*;
import com.scorbutics.maven.service.filesystem.source.*;

import lombok.*;

@Builder
public class ProjectMavenReactorAnalyzer implements ProjectComputer {

	private final FileSystemSourceReader fileSystemSourceReader;
	private final Log                    logger;

	@Override
	public Optional<ComputedProject> compute( final List<MavenProject> allProjects ) {
		logger.debug( "Computing project structure using Maven Reactor information... " + allProjects.size() + " projects found." );

		final List<ComputedModule> modules = allProjects.stream()
				// We don't want parent module here
				.filter(project -> !XMLModule.PARENT_PACKAGING_TYPE.equals(project.getPackaging()))
				// Flatten modules
				.map(project -> XMLModule.fromMavenProject(project, logger).flatMap( module -> module.buildComputedModule( new ArrayList<>(), 0, project, fileSystemSourceReader, logger )))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect( Collectors.toList());

		return Optional.of(ComputedProject.builder().modules(modules).build());

	}
}