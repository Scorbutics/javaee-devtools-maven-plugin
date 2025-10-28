package com.scorbutics.maven.model.packaging;

import lombok.*;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.*;
import org.apache.maven.project.MavenProject;


import javax.xml.bind.JAXBException;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;


@EqualsAndHashCode( callSuper = true )
@AllArgsConstructor
@NoArgsConstructor
@Data
@XmlRootElement(name = "configuration")
@XmlAccessorType(XmlAccessType.FIELD)
public class WarXMLPluginConfiguration
		extends XMLContainerPluginConfiguration {
    private String webappDirectory;
    private String warSourceDirectory;
    private String warSourceExcludes;
}