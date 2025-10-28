package com.scorbutics.maven.model.packaging;

import java.io.*;

import javax.xml.bind.*;

import org.apache.maven.model.*;
import org.apache.maven.plugin.logging.*;
import org.apache.maven.project.*;

public class PluginConfigurationReader {
	public static <T> T fromPluginAndProject( final Class<T> clazz, final Plugin plugin, final MavenProject project ) throws RuntimeException {
		try {
			final JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
			final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			@SuppressWarnings( "unchecked" )
			final T configuration = (T) unmarshaller.unmarshal(new ByteArrayInputStream(plugin.getConfiguration().toString().getBytes()));
			// Add a virtual root module representing the project itself
			return configuration;
		} catch ( final Exception e ) {
			throw new RuntimeException("Error while unmarshalling plugin configuration for project '" + project.getArtifactId() + "' using class '" + clazz + "' with packaging " + project.getPackaging(), e);
		}
	}
}