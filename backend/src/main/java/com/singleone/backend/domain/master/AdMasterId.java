package com.singleone.backend.domain.master;

import java.io.Serializable;
import java.util.Objects;

import com.singleone.backend.domain.common.Media;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * PRD 5.4: Ad Natural/Business Identity = advertiser_id + media + campaign_id + ad_group_id + ad_id.
 */
@Embeddable
public class AdMasterId implements Serializable {

	@Column(name = "advertiser_id", length = 100)
	private String advertiserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "media", length = 20)
	private Media media;

	@Column(name = "campaign_id", length = 100)
	private String campaignId;

	@Column(name = "ad_group_id", length = 100)
	private String adGroupId;

	@Column(name = "ad_id", length = 100)
	private String adId;

	protected AdMasterId() {
	}

	public AdMasterId(String advertiserId, Media media, String campaignId, String adGroupId, String adId) {
		this.advertiserId = advertiserId;
		this.media = media;
		this.campaignId = campaignId;
		this.adGroupId = adGroupId;
		this.adId = adId;
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

	public String getAdGroupId() {
		return adGroupId;
	}

	public String getAdId() {
		return adId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AdMasterId that)) {
			return false;
		}
		return Objects.equals(advertiserId, that.advertiserId)
			&& media == that.media
			&& Objects.equals(campaignId, that.campaignId)
			&& Objects.equals(adGroupId, that.adGroupId)
			&& Objects.equals(adId, that.adId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(advertiserId, media, campaignId, adGroupId, adId);
	}

}
