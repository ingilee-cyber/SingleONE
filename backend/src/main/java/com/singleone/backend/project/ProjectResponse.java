package com.singleone.backend.project;

import java.time.Instant;
import java.util.List;

public record ProjectResponse(
	Long projectId,
	String advertiserId,
	String projectName,
	boolean systemDefault,
	boolean referenceOnly,
	List<CampaignOptionResponse> campaigns,
	Instant createdAt,
	Instant updatedAt
) {
}
