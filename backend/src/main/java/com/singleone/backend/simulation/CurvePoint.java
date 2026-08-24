package com.singleone.backend.simulation;

import java.math.BigDecimal;

/** PRD 10.8 매체별 한계 효율 곡선의 표본점 한 개. */
public record CurvePoint(BigDecimal weeklyCost, BigDecimal predictedPurchases, BigDecimal predictedRevenue) {
}
