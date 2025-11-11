package com.scorbutics.maven.model;

import org.apache.maven.plugins.annotations.*;

import lombok.*;

@Data
public class WatcherConfiguration {

	@Parameter(property = "verbose")
	private boolean verbose = false;

	@Parameter(property = "showProgress")
	private boolean showProgress = true;

	@Parameter(property = "threads")
	private int threads = 2;

	@Parameter(property = "debounce")
	private int debounce = 200;

    @Parameter(property = "triggerRedeploymentDelay")
    private int triggerRedeploymentDelay = 500;
}