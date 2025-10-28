package com.scorbutics.maven;

import java.util.function.*;

import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;

import com.scorbutics.maven.model.*;
import com.scorbutics.maven.model.packaging.*;

@Mojo(name = "watch-web", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class MojoWatchWebPlugin
		extends MojoWatchAllPlugin {

	@Override
	protected Predicate<Deployment> filterDeployment() {
		return deployment -> deployment.getPackaging() == Packaging.WAR;
	}

}