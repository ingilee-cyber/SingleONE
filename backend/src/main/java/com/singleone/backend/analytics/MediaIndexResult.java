package com.singleone.backend.analytics;

import java.math.BigDecimal;

import com.singleone.backend.domain.common.Media;

/**
 * 매체 1개에 대한 Index 산출 결과. status가 VALID일 때만 indexScore/components가 채워진다.
 * rawTotals/rawPerformance/singleOnePerformance는 MISSING_REQUIRED_DATA일 때만 null이다
 * (PRD 8.6: 조건 미달이어도 원본/SingleONE 성과 자체는 표시).
 */
public record MediaIndexResult(
	Media media,
	IndexStatus status,
	MediaPerformanceTotals rawTotals,
	OriginalPerformance rawPerformance,
	SingleOnePerformance singleOnePerformance,
	IndexComponents components,
	BigDecimal indexScore
) {
}
