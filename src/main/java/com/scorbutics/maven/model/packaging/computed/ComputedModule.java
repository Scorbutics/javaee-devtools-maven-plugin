package com.scorbutics.maven.model.packaging.computed;

import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.apache.maven.project.*;

import com.scorbutics.maven.model.packaging.*;

import lombok.*;
import lombok.experimental.*;

@Builder(toBuilder = true)
@Value
@ToString
public class ComputedModule {
	@ToString.Exclude
	MavenProject project;

	Path baseDirectory;
	Path sourceDirectory;
	String finalName;
	String    extension;
	Packaging packaging;

	boolean redeployOnChange;

	// Can be filled when checking the deployed filesystem
	// Or when analyzing the project structure using plugins
	int depth;
	@ToString.Exclude
	List<ComputedModule> children;

	// Only filled when checking the deployed filesystem
	Path deployedDirectory;

	public Stream<ComputedModule> flattenedStream() {
		return Stream.concat(
			Stream.of(this),
			children.stream().flatMap(ComputedModule::flattenedStream)
		);
	}

	public String getFinalNameWithExtension() {
		return finalName + "." + extension;
	}

	public ComputedModule rebuildEachModule( final Function<ComputedModule, ComputedModuleBuilder> mapper ) {
		return mapper.apply(this).children(
			children.stream()
				.map( child -> child.rebuildEachModule(mapper) )
				.collect(Collectors.toList())
		).build();
	}
}