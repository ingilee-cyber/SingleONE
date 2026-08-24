package com.singleone.backend.simulation;

import java.math.BigDecimal;
import java.util.List;

import com.singleone.backend.domain.common.Media;

/**
 * PRD 10.7/10.8 매체 하나의 시뮬레이션 결과. confidence는 입력 예산이 0이면 해당 사항이 없어
 * null이다(PRD 10.7 표에 이 경우의 등급이 명시돼 있지 않음). {@link ConfidenceLevel#UNAVAILABLE}이면
 * predictedPurchases/Revenue/Cpa/Roas는 전부 null이다("예측 불가"). curvePoints는 모델이
 * 유효할 때만 채워지며 x축(weeklyCost) 기준이다. notes는 PRD 10.8이 예시로 든 "효율 감소
 * 관찰"/"과거 운영 범위 초과"/"데이터 부족"/"포화구간 진입 가능성" 같은 중립적 관찰 문구 목록이며,
 * 추천/최적화 표현은 포함하지 않는다(Hard Rule 6).
 */
public record MediaSimulationResult(
	Media media,
	BigDecimal inputBudget,
	BigDecimal weeklyBudget,
	BigDecimal convertedCurrentBudget,
	BigDecimal convertedCurrentWeeklyBudget,
	ConfidenceLevel confidence,
	BigDecimal predictedPurchases,
	BigDecimal predictedRevenue,
	BigDecimal predictedCpa,
	BigDecimal predictedRoas,
	BigDecimal historicalMinWeeklyCost,
	BigDecimal historicalMaxWeeklyCost,
	List<CurvePoint> curvePoints,
	List<String> notes
) {
}
