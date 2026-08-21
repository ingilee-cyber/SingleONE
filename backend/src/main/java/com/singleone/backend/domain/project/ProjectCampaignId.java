package com.singleone.backend.domain.project;

import java.io.Serializable;
import java.util.Objects;

import com.singleone.backend.domain.common.Media;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * PRD 5.4: 프로젝트의 캠페인 참조 = advertiser_id + media + campaign_id 복합키.
 * 여기에 project_id를 더해 "프로젝트 안에서는 동일 캠페인 한 번만" 규칙(PRD 5.1)을 PK로 강제한다.
 */
@Embeddable
public class ProjectCampaignId implements Serializable {

	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "advertiser_id", length = 100)
	private String advertiserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "media", length = 20)
	private Media media;

	@Column(name = "campaign_id", length = 100)
	private String campaignId;

	protected ProjectCampaignId() {
	}

	public ProjectCampaignId(Long projectId, String advertiserId, Media media, String campaignId) {
		this.projectId = projectId;
		this.advertiserId = advertiserId;
		this.media = media;
		this.campaignId = campaignId;
	}

	public Long getProjectId() {
		return projectId;
	}

	public String getAdvertiserId() {
		return advertiserId;
	}

	public Media getMedia() {
		return media;
	}

	public String getCampaignId() {
		return campaignId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ProjectCampaignId that)) {
			return false;
		}
		return Objects.equals(projectId, that.projectId)
			&& Objects.equals(advertiserId, that.advertiserId)
			&& media == that.media
			&& Objects.equals(campaignId, that.campaignId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(projectId, advertiserId, media, campaignId);
	}

}
