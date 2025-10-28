package com.scorbutics.maven.model;

import lombok.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.*;

import com.scorbutics.maven.model.packaging.*;

@ToString
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class Deployment implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @EqualsAndHashCode.Include
    private Path source;
    @EqualsAndHashCode.Include
    private Path target;

	private Path base;

	private Packaging packaging;

    private boolean unpack;
    private boolean redeployOnChange;
    private boolean useSourceFilesystemOnly;

	private int depth = Integer.MIN_VALUE;

	private transient boolean               computed;
	private transient Map<Path, List<Deployment>> children = new HashMap<>();

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

	public void normalizePaths(final Path basePath, final Path targetPath) {
		if (!source.isAbsolute()) {
			source = basePath.resolve(source);
		}

		if (!target.isAbsolute()) {
			target = (useSourceFilesystemOnly ? basePath : targetPath).resolve(source);
		}

		if (base != null && !base.isAbsolute()) {
			base = basePath.resolve(base);
		}
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