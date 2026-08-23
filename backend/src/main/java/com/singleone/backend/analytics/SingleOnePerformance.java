package com.singleone.backend.analytics;

import java.math.BigDecimal;

import com.singleone.backend.domain.common.Media;

/**
 * PRD 8.4 SingleONE 성과. cpa/roas는 계산 불가한 경우(purchases=0 또는 cost=0) null이다.
 * roas는 백분율 값(예: 150은 150%)이다.
 */
public record SingleOnePerformance(
	Media media,
	BigDecimal singleOnePurchases,
	BigDecimal singleOneRevenue,
	BigDecimal cpa,
	BigDecimal roas
) {
}
