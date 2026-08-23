package com.singleone.backend.dashboard;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.singleone.backend.analytics.MediaIndexResult;
import com.singleone.backend.analytics.PeriodComparison;
import com.singleone.backend.analytics.SingleOneIndexCalculator;
import com.singleone.backend.analytics.SingleOnePerformanceService;

/**
 * PRD 6장 Dashboard 조합 계층. 계산은 전부 {@link SingleOnePerformanceService}/
 * {@link SingleOneIndexCalculator}(Stage 3)에 위임하고, 여기서는 응답 조립과 정렬만 한다.
 * 이전 기간은 항상 함께 계산해 반환하며(추가 호출 1회로 비용이 크지 않음), 비교 표시 여부는
 * Frontend 토글에서 결정한다.
 */
@Service
public class DashboardService {

	private final SingleOnePerformanceService performanceService;
	private final SingleOneIndexCalculator calculator;

	public DashboardService(SingleOnePerformanceService performanceService, SingleOneIndexCalculator calculator) {
		this.performanceService = performanceService;
		this.calculator = calculator;
	}

	public DashboardResponse getDashboard(Long projectId, LocalDate from, LocalDate to) {
		PeriodComparison comparison = performanceService.calculatePeriodWithPreviousComparison(projectId, from, to);
		List<MediaIndexResult> current = sortByScoreDescending(comparison.current());
		List<MediaIndexResult> previous = sortByScoreDescending(comparison.previous());

		return new DashboardResponse(
			current,
			calculator.aggregateProjectTotals(comparison.current()),
			previous,
			calculator.aggregateProjectTotals(comparison.previous()),
			performanceService.calculateRollingIndex(projectId, from, to));
	}

	/** PRD 6.3: 매체별 Index 점수 목록은 점수 높은 순으로 정렬한다(점수가 없는 매체는 뒤로 보낸다). */
	private List<MediaIndexResult> sortByScoreDescending(List<MediaIndexResult> results) {
		return results.stream()
			.sorted(Comparator.comparing(MediaIndexResult::indexScore, Comparator.nullsLast(Comparator.reverseOrder())))
			.toList();
	}

}
