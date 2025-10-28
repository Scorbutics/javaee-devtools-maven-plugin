package com.scorbutics.maven.model.packaging;


import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.nio.file.Path;
import java.nio.file.Paths;

@XmlRootElement(name = "jarModule")
@XmlAccessorType(XmlAccessType.FIELD)
public class JarXMLModule
		extends XMLModule {

    @Override
    protected Path computeSourceDirectory( final MavenProject project, final Log logger) {
        // Jars are not complex archives, so we assume the output directory is the build directory
        // in the opposite of wars or ears
        return Paths.get(project.getBuild().getDirectory()).resolve("classes");
    }

    @Override
    public boolean redeployOnChange() {
        return true;
    }

    @Override
    public Packaging getPackaging() {
        return Packaging.JAR;
    }
}