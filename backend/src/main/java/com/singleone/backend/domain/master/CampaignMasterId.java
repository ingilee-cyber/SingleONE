package com.singleone.backend.domain.master;

import java.io.Serializable;
import java.util.Objects;

import com.singleone.backend.domain.common.Media;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

/**
 * PRD 5.4: Campaign Natural/Business Identity = advertiser_id + media + campaign_id.
 */
@Embeddable
public class CampaignMasterId implements Serializable {

	@Column(name = "advertiser_id", length = 100)
	private String advertiserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "media", length = 20)
	private Media media;

	@Column(name = "campaign_id", length = 100)
	private String campaignId;

	protected CampaignMasterId() {
	}

	public CampaignMasterId(String advertiserId, Media media, String campaignId) {
		this.advertiserId = advertiserId;
		this.media = media;
		this.campaignId = campaignId;
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
		if (!(o instanceof CampaignMasterId that)) {
			return false;
		}
		return Objects.equals(advertiserId, that.advertiserId)
			&& media == that.media
			&& Objects.equals(campaignId, that.campaignId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(advertiserId, media, campaignId);
	}

}
