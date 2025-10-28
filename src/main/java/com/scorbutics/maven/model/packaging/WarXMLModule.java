package com.scorbutics.maven.model.packaging;

import lombok.*;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@EqualsAndHashCode( callSuper = true )
@XmlRootElement(name = "warModule")
@XmlAccessorType(XmlAccessType.FIELD)
@Data
public class WarXMLModule
		extends XMLModule {

    @Override
    protected Path computeSourceDirectory(final MavenProject project, final Log logger) {
		// TODO retrieve plugin configuration from the module?
        return ((List<Plugin>) project.getBuildPlugins()).stream()
                .filter(p -> p.getArtifactId().equals(getPackaging().getPluginId()))
                .findFirst()
                .map(plugin -> {
                    try {
//                        logger.info("maven-war-plugin found for project " + project.getArtifactId() + ": " + plugin.getVersion());
                        final WarXMLPluginConfiguration configuration = PluginConfigurationReader.fromPluginAndProject(WarXMLPluginConfiguration.class, plugin, project);

                        // TODO add flexibility thanks to extra plugin configuration?
                        if (configuration.getWebappDirectory() != null) {
                            return project.getBasedir().toPath().resolve(configuration.getWebappDirectory());
                        }
                    } catch (final RuntimeException e) {
                        logger.warn("Error parsing " + getPackaging().getPluginId() + " configuration for project " + project.getArtifactId() + ". Using default source directory.", e);
                    }
                    return Paths.get(project.getBuild().getDirectory()).resolve(Paths.get(project.getBuild().getFinalName()));
                })
                .orElse(null);
    }

    @Override
    public Packaging getPackaging() {
        return Packaging.WAR;
    }
}