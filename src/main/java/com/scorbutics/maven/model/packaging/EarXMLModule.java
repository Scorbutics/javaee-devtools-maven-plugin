package com.scorbutics.maven.model.packaging;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.nio.file.Paths;

public class EarXMLModule
		extends XMLModule {

    @Override
    protected Path computeSourceDirectory(final MavenProject project, final Log logger) {
        return Paths.get(project.getBuild().getDirectory()).resolve(Paths.get(project.getBuild().getFinalName()));
    }

    @Override
    public Packaging getPackaging() {
        return Packaging.EAR;
    }
}