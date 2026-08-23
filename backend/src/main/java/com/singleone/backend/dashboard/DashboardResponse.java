package com.singleone.backend.dashboard;

import java.util.List;

import com.singleone.backend.analytics.MediaIndexResult;
import com.singleone.backend.analytics.ProjectTotals;
import com.singleone.backend.analytics.RollingIndexPoint;

/**
 * PRD 6장 Dashboard 응답. Stage 3 analytics record를 그대로 실어 보내며 별도 매핑 계층을
 * 두지 않는다(계산은 전부 {@code SingleOnePerformanceService}/{@code SingleOneIndexCalculator}에서
 * 이미 끝난 값).
 */
public record DashboardResponse(
	List<MediaIndexResult> current,
	ProjectTotals currentTotals,
	List<MediaIndexResult> previous,
	ProjectTotals previousTotals,
	List<RollingIndexPoint> rolling
) {
}
