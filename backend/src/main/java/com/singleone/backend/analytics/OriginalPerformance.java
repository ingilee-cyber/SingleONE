package com.singleone.backend.analytics;

import java.math.BigDecimal;

/**
 * PRD 8.7: 원본(비SingleONE) 효율 참고 지표. purchases/revenue 자체는 {@link MediaPerformanceTotals}에
 * 이미 있으므로 여기서는 파생 비율만 담는다. cpa/roas는 계산 불가한 경우(원본 purchases=0 또는 cost=0)
 * null이다.
 */
public record OriginalPerformance(BigDecimal cpa, BigDecimal roas) {
}
