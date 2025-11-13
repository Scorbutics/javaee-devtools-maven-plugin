package com.scorbutics.maven.model;

import lombok.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.*;

import com.scorbutics.maven.model.packaging.*;

@ToString
@Value
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@Builder(toBuilder = true)
public class Deployment implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @EqualsAndHashCode.Include
    Path source;
    @EqualsAndHashCode.Include
    Path target;

	Path base;
	Path archive;

    @Builder.Default
	boolean enabled = true;

	Packaging packaging;

    boolean unpack;
    boolean redeployOnChange;
    boolean useSourceFilesystemOnly;

	@Builder.Default
	transient int depth = Integer.MIN_VALUE;

	transient boolean               computed;
	@Builder.Default
	transient Map<Path, List<Deployment>> children = new HashMap<>();

    public Path getEnclosingTargetArchive(final Path basePath) {
        final Path path = basePath.relativize(this.target);
        if (path.getNameCount() > 0) {
            return path.getName(0);
        }
        return null;
    }

	public DeploymentConfigurationError validate() {
		final boolean mandatoryData = this.source != null && this.target != null;
		if (!mandatoryData) {
			return DeploymentConfigurationError.MANDATORY_DATA_MISSING;
		}

		if (this.base != null) {
			final boolean baseShouldBeIncludedInSource = this.source.startsWith(this.base);
			if ( !baseShouldBeIncludedInSource ) {
				return DeploymentConfigurationError.BASE_NOT_IN_SOURCE;
			}
		}
		return DeploymentConfigurationError.NONE;
	}

	public Deployment normalizePaths(final Path basePath, final Path targetPath) {
		final Path source = !this.source.isAbsolute() ? basePath.resolve(this.source) : this.source;
        final Path target = !this.target.isAbsolute()
                ? (useSourceFilesystemOnly ? basePath : targetPath).resolve(source)
                : this.target;
        final Path base = this.base != null && !this.base.isAbsolute() ? basePath.resolve(this.base) : this.base;
        return this.toBuilder()
                .source(source)
                .target(target)
                .base(base)
                .build();
	}

	public Set<Path> computeDirectSubtrees() {
		return children.values().stream()
				.flatMap( List::stream )
				// keep only subdeployments that are direct children of this deployment in target path
				.filter( deployment -> deployment.getTarget().startsWith(target) )
				// rephrase the relativized path to be resolved to this deployment source
				.map( deployment -> source.resolve(target.relativize(deployment.getTarget())))
				.collect( Collectors.toSet());
	}

	public Stream<Deployment> flatten() {
		return Stream.concat(
				Stream.of(this),
				children.values().stream().flatMap( list -> list.stream().flatMap( Deployment::flatten ) )
		);
	}
}