package com.singleone.backend.simulation;

import java.math.BigDecimal;
import java.util.List;

/**
 * PRD 10.8 전체 응답. AC-52: 예산이 0보다 큰 매체 중 하나라도 예측 불가면 total* 필드는 전부
 * null("산출 불가")이 되지만, {@code mediaResults}의 개별 매체 결과는 계속 제공한다.
 */
public record SimulationResult(
	List<MediaSimulationResult> mediaResults,
	BigDecimal totalBudget,
	boolean totalAvailable,
	BigDecimal totalPredictedPurchases,
	BigDecimal totalPredictedRevenue,
	BigDecimal totalPredictedCpa,
	BigDecimal totalPredictedRoas,
	String disclaimer
) {
	public static final String DISCLAIMER = "예상 성과는 과거 운영 데이터를 기반으로 한 시뮬레이션 값이며 실제 광고 성과를 보장하지 않습니다.";
}
