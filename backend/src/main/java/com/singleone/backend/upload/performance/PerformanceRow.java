package com.singleone.backend.upload.performance;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.singleone.backend.domain.common.Media;

/**
 * PRD 11.2 성과 데이터 필드, PRD 11.3 고유키(date+advertiser_id+media+campaign_id+ad_group_id+ad_id).
 */
public record PerformanceRow(
	LocalDate date,
	String advertiserId,
	String advertiserName,
	Media media,
	String campaignId,
	String campaignName,
	String adGroupId,
	String adGroupName,
	String adId,
	String adName,
	long impressions,
	long clicks,
	BigDecimal cost,
	long addToCart,
	long purchases,
	BigDecimal purchaseRevenue
) {

	public String naturalKey() {
		return String.join("|", date.toString(), advertiserId, media.name(), campaignId, adGroupId, adId);
	}

}
