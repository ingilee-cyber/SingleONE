package com.singleone.backend.detail;

import com.singleone.backend.analytics.MediaPerformanceTotals;
import com.singleone.backend.analytics.OriginalPerformance;
import com.singleone.backend.analytics.SingleOnePerformance;

/**
 * PRD 7.3~7.5: 캠페인/광고그룹/광고 공용 성과(원본+SingleONE). Index 상태 개념이 없는
 * 레벨이라 {@code MediaIndexResult}를 재사용하지 않고 필요한 필드만 담는다.
 */
public record EntityPerformance(
	String id,
	String name,
	MediaPerformanceTotals rawTotals,
	OriginalPerformance rawPerformance,
	SingleOnePerformance singleOnePerformance
) {
}
