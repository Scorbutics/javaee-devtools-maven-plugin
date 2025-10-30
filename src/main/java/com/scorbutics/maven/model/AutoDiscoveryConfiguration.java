package com.scorbutics.maven.model;

import org.apache.maven.plugins.annotations.*;

import lombok.*;

@Data
public class AutoDiscoveryConfiguration {
	@Parameter(property = "enabled")
	private boolean enabled = true;

	@Parameter(property = "maxDeployedModulesDepthCheck")
	private int maxDeployedModulesDepthCheck = 5;
}