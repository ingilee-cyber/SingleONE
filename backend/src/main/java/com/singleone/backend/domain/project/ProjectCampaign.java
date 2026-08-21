package com.singleone.backend.domain.project;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * PRD 12.1 ProjectCampaign: project_id + campaign composite reference.
 */
@Entity
@Table(name = "project_campaign")
public class ProjectCampaign {

	@EmbeddedId
	private ProjectCampaignId id;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected ProjectCampaign() {
	}

	public ProjectCampaign(ProjectCampaignId id) {
		this.id = id;
	}

	public ProjectCampaignId getId() {
		return id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
