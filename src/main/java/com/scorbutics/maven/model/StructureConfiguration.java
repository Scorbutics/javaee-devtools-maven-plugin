package com.scorbutics.maven.model;

import org.apache.maven.plugins.annotations.*;

import lombok.*;

@Data
public class StructureConfiguration {

	@NonNull
	@Parameter(property = "autoDiscovery")
	private AutoDiscoveryConfiguration autoDiscovery = new AutoDiscoveryConfiguration();
}