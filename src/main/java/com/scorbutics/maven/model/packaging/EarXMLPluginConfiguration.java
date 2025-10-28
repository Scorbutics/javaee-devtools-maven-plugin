package com.scorbutics.maven.model.packaging;

import lombok.*;


import javax.xml.bind.annotation.*;

@EqualsAndHashCode( callSuper = true )
@AllArgsConstructor
@NoArgsConstructor
@Data
@XmlRootElement(name = "configuration")
@XmlAccessorType(XmlAccessType.FIELD)
public class EarXMLPluginConfiguration
		extends XMLContainerPluginConfiguration {
    private Integer version;
    private String defaultLibBundleDir;
    private String outputFileNameMapping;
    private Boolean skinnyWars;
}