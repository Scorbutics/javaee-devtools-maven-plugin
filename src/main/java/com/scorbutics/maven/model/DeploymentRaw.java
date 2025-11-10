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

    private boolean enabled = true;

    private Packaging packaging;

    private boolean unpack;
    private boolean redeployOnChange;
    private boolean useSourceFilesystemOnly;

    public Deployment toDeployment() {
        return Deployment.builder()
                .source(this.source == null ? null : Paths.get(this.source))
                .target(this.target == null ? null : Paths.get(this.target))
                .base(this.base == null ? null : Paths.get(this.base))
                .archive(this.archive == null ? null : Paths.get(this.archive))
                .enabled(this.enabled)
                .packaging(this.packaging)
                .unpack(this.unpack)
                .redeployOnChange(this.redeployOnChange)
                .useSourceFilesystemOnly(this.useSourceFilesystemOnly)
                .build();
    }
}
