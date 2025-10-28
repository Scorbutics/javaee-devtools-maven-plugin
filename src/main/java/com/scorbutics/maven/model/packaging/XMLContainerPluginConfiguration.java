package com.scorbutics.maven.model.packaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.xml.bind.*;
import javax.xml.bind.annotation.*;

import java.io.*;
import java.util.*;

import org.apache.maven.model.*;
import org.apache.maven.plugin.logging.*;
import org.apache.maven.project.*;

@AllArgsConstructor
@NoArgsConstructor
@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class XMLContainerPluginConfiguration {

    @XmlElementWrapper(name = "modules")
    @XmlElements({
            @XmlElement(name = "jarModule", type = JarXMLModule.class),
            @XmlElement(name = "ejbModule", type = EjbXMLModule.class),
            @XmlElement(name = "webModule", type = WarXMLModule.class)
    })
    private List<XMLModule> modules = new ArrayList<>();

	private transient XMLModule rootModule;

	public static <T extends XMLContainerPluginConfiguration> T fromPluginAndProject( final Class<T> clazz, final Plugin plugin, final MavenProject project, final Log logger ) throws RuntimeException {
		final T configuration = PluginConfigurationReader.fromPluginAndProject(clazz, plugin, project);
		// Add a virtual root module representing the project itself
		configuration.setRootModule(XMLModule.fromMavenProject(project, logger).orElseThrow(() -> new IllegalStateException("Unable to create XMLModule from project " + project.getArtifactId())));
		return configuration;
	}
}