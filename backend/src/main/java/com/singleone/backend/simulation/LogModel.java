package com.singleone.backend.simulation;

import java.math.BigDecimal;

/**
 * PRD 10.4/10.5: y = a·ln(x) + b OLS 적합 결과. valid는 "증가형+한계 효율 감소형"(a&gt;0)과
 * "모델 적합도"(R²&gt;=0.50) 두 조건만 담당한다 — 유효 주차 수/SingleONE 구매 합계/비용 변동폭은
 * 주차 집합 전체에 대한 조건이라 {@link SimulationService}에서 별도로 판정한다.
 */
public record LogModel(BigDecimal a, BigDecimal b, BigDecimal rSquared, boolean valid) {

	public static final LogModel INVALID = new LogModel(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
}
