package com.scorbutics.maven.model.packaging;

import com.scorbutics.maven.model.packaging.computed.*;
import com.scorbutics.maven.service.filesystem.source.FileSystemSourceReader;

import lombok.*;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.nio.file.Path;
import java.util.*;

@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@XmlAccessorType(XmlAccessType.FIELD)
@ToString(onlyExplicitlyIncluded = true)
public abstract class XMLModule {

	public static final String PARENT_PACKAGING_TYPE = "pom";

	@EqualsAndHashCode.Include
	@ToString.Include
    private String groupId;

	@EqualsAndHashCode.Include
	@ToString.Include
    private String artifactId;

    private String bundleFileName;
    private String unpack;

	/**
	 * Returns a unique identifier for this module
	 */
	public String getIdentifier() {
		return groupId + ":" + artifactId;
	}

    public static Optional<XMLModule> fromMavenProject(final MavenProject project, final Log logger) {

		final XMLModule module;
		try {
			module = Packaging.fromPackaging(project.getPackaging()).getModuleClass().newInstance();
		} catch ( final IllegalArgumentException | InstantiationException | IllegalAccessException e ) {
			logger.warn("Unsupported packaging type '" + project.getPackaging() + "' for project " + project.getArtifactId() + ". Supported types are " + Arrays.toString( Packaging.values() ), e);
			return Optional.empty();
		}

		module.setGroupId(project.getGroupId());
        module.setArtifactId(project.getArtifactId());
        module.setBundleFileName(project.getBuild().getFinalName() + "." + project.getPackaging());
        return Optional.of(module);
    }

	protected abstract Path computeSourceDirectory(MavenProject project, Log logger);

    public final Optional<ComputedModule> buildComputedModule(final List<ComputedModule> children, final int depth, final MavenProject project, final FileSystemSourceReader fileSystemSourceReader, final Log logger) {
		final Path baseDirectory = project.getBasedir().toPath();

		final Path sourceDirectory = fallbackToDefaultSourceDirectory(computeSourceDirectory(project, logger), baseDirectory);
        if (!fileSystemSourceReader.isDirectory(sourceDirectory)) {
            logger.warn("Source directory for module '" + sourceDirectory + "' not found.");
            return Optional.empty();
        }

		final String finalName = (project.getBuild().getFinalName() == null ? artifactId : project.getBuild().getFinalName());

        return Optional.of(ComputedModule.builder()
						.depth(depth)
						.children(children)
						.baseDirectory(baseDirectory)
						.extension(getPackaging().getExtension())
						.packaging(getPackaging())
						.sourceDirectory(sourceDirectory)
						.finalName(finalName)
						.redeployOnChange(redeployOnChange())
						.project(project)
						.build());
    }

	private Path fallbackToDefaultSourceDirectory(final Path sourceDirectory, final Path baseDirectory) {
		if (sourceDirectory != null) {
			return sourceDirectory;
		}

		return baseDirectory.resolve(this.bundleFileName).normalize();
	}

	public boolean redeployOnChange() {
        return false;
    }

    public abstract Packaging getPackaging();

	/**
	 * Creates a cycle-breaking module to avoid infinite recursion in case of cyclic dependencies.
	 *
	 * @return A new XMLModule instance that indicates a cycle has been detected.
	 */
	public XMLModule toCycleBreakingModule() {
		final XMLModule current = this;
		return new XMLModule(
				groupId,
				artifactId + " (cycle)",
				bundleFileName,
				unpack
		) {
			@Override
			protected Path computeSourceDirectory(final MavenProject project, final Log logger) {
				return current.computeSourceDirectory(project, logger);
			}

			@Override
			public Packaging getPackaging() {
				return current.getPackaging();
			}
		};
	}
}