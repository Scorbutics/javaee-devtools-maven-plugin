package com.scorbutics.maven.service.packaging;

import java.util.*;

import org.apache.maven.project.*;

import com.scorbutics.maven.model.packaging.computed.*;

public interface ProjectComputer {
	Optional<ComputedProject> compute(final List<MavenProject> allProjects);
}