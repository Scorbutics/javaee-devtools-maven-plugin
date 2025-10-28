package com.scorbutics.maven.model.packaging;

import lombok.*;

@Getter
public enum Packaging {
	EAR("ear", EarXMLModule.class, EarXMLPluginConfiguration.class, "maven-ear-plugin", true, true),
	JAR("jar", JarXMLModule.class, null,"maven-jar-plugin", false, false),
	WAR("war", WarXMLModule.class, WarXMLPluginConfiguration.class, "maven-war-plugin", true, false),
	EJB("jar", EjbXMLModule.class, null, "maven-ejb-plugin", false, false);

	private final String extension;
	private final Class<? extends XMLModule>                       moduleClass;
	private final Class<? extends XMLContainerPluginConfiguration> pluginConfigurationClass;
	private final String                                           pluginId;
	private final boolean 										   isContainer;
	private final boolean 										   hasPlugingConfigurationSubmodules;

	Packaging(final String extension, final Class<? extends XMLModule> moduleClass, final Class<? extends XMLContainerPluginConfiguration> pluginConfigurationClass, final String pluginId, final boolean isContainer, final boolean hasPlugingConfigurationSubmodules) {
		this.extension = extension;
		this.moduleClass = moduleClass;
		this.pluginId = pluginId;
		this.pluginConfigurationClass = pluginConfigurationClass;
		this.isContainer = isContainer;
		this.hasPlugingConfigurationSubmodules = hasPlugingConfigurationSubmodules;
	}

	public static Packaging fromPackaging(final String packaging) {
		final Packaging result = fromPackagingOrNull(packaging);
		if (result != null) {
			return result;
		}
		throw new IllegalArgumentException("No packaging found: " + packaging);
	}

	private static Packaging fromPackagingOrNull(final String packaging) {
		for (final Packaging value : Packaging.values()) {
			if (value.name().equalsIgnoreCase(packaging)) {
				return value;
			}
		}
		return null;
	}

	public static boolean excluded(final String packaging) {
		return fromPackagingOrNull(packaging) == null;
	}

}