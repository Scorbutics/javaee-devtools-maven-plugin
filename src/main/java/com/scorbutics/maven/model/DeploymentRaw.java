package com.scorbutics.maven.model;

import com.scorbutics.maven.model.packaging.Packaging;
import lombok.*;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Path;
import java.nio.file.Paths;

@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class DeploymentRaw {

    @Parameter(property = "source", required = true)
    private String source;

    @Parameter(property = "target", required = true)
    private String target;

    @Parameter(property = "base")
    private String base;

    @Parameter(property = "archive")
    private String archive;

    @Builder.Default
    private boolean enabled = true;

    private Packaging packaging;

    private boolean unpack;
    private boolean redeployOnChange;
    private boolean useSourceFilesystemOnly;

    public Deployment toDeployment() {
        final Path source = this.source == null ? null : Paths.get(this.source);
        return Deployment.builder()
                .source(source)
                .target(this.target == null ? null : Paths.get(this.target))
                // If base is not set, default to source's parent
                .base(this.base == null ? (source == null ? null : source.getParent()) : Paths.get(this.base))
                .archive(this.archive == null ? null : Paths.get(this.archive))
                .enabled(this.enabled)
                .packaging(this.packaging)
                .unpack(this.unpack)
                .redeployOnChange(this.redeployOnChange)
                .useSourceFilesystemOnly(this.useSourceFilesystemOnly)
                .build();
    }
}
