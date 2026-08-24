package com.singleone.backend.analytics;

import java.math.BigDecimal;

/**
 * PRD 7장 상세 화면용: 기간 전체를 (campaign_id, ad_group_id, ad_id) 단위로 합산한 원본 성과 한 행.
 * {@link PerformanceAggregationRepository#fetchEntityTotals}의 결과 shape이다.
 */
public record EntityAggregateRow(
	String campaignId,
	String adGroupId,
	String adId,
	BigDecimal impressions,
	BigDecimal clicks,
	BigDecimal cost,
	BigDecimal rawPurchases,
	BigDecimal rawRevenue
) {
}
