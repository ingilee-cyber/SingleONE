package com.singleone.backend.analytics;

import java.math.BigDecimal;

/**
 * PRD 6.2 Dashboard KPI 카드용 프로젝트 전체 합계. 매체별로 이미 계산된 값을 더한 것뿐이며
 * 새로운 비율 계산은 ROAS(합계 기준 재도출)뿐이다. cost=0이면 ROAS는 null이다.
 */
public record ProjectTotals(
	BigDecimal impressions,
	BigDecimal clicks,
	BigDecimal cost,
	BigDecimal rawPurchases,
	BigDecimal rawRevenue,
	BigDecimal rawRoas,
	BigDecimal singleOnePurchases,
	BigDecimal singleOneRevenue,
	BigDecimal singleOneRoas
) {
}
