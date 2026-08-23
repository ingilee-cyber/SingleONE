package com.singleone.backend.analytics;

import java.math.BigDecimal;

import com.singleone.backend.domain.common.Media;

/**
 * 선택 프로젝트·기간에 대해 매체 단위로 집계된 원본 성과 합계(PRD 8.4). operatingDays는
 * cost&gt;0인 distinct date 수다(PRD 8.6).
 */
public record MediaPerformanceTotals(
	Media media,
	BigDecimal impressions,
	BigDecimal clicks,
	BigDecimal cost,
	BigDecimal rawPurchases,
	BigDecimal rawRevenue,
	int operatingDays
) {
}
