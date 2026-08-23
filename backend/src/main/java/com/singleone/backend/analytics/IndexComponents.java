package com.singleone.backend.analytics;

import java.math.BigDecimal;

/**
 * PRD 8.5/6.3: Index를 구성하는 4개 효율의 개별 상대 지수(각각 유효 매체 평균=100 기준).
 * Dashboard의 "SingleONE Index 구성요소 Breakdown"에 사용한다. VALID 매체에만 값이 있다.
 */
public record IndexComponents(
	BigDecimal exposureIndex,
	BigDecimal clickIndex,
	BigDecimal purchaseIndex,
	BigDecimal revenueIndex
) {
}
