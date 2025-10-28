package com.scorbutics.maven;

import lombok.*;

@RequiredArgsConstructor
@Getter
public enum DeploymentType {
	HOT_DEPLOYMENT( true, false ),
	FULL_DEPLOYMENT( false, true ),
	UNIT_DEPLOYMENT( true, false );

	private final boolean forceTargetCreation;

	private final boolean isArchive;
}