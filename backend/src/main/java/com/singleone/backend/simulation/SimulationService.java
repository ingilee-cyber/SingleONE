package com.singleone.backend.simulation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.singleone.backend.analytics.DailyMediaTotal;
import com.singleone.backend.analytics.MediaPerformanceTotals;
import com.singleone.backend.analytics.PerformanceAggregationRepository;
import com.singleone.backend.analytics.SingleOneIndexCalculator;
import com.singleone.backend.analytics.SingleOnePerformance;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.filter.InternalMediaFilterRepository;
import com.singleone.backend.domain.project.Project;
import com.singleone.backend.domain.project.ProjectCampaignId;
import com.singleone.backend.domain.project.ProjectRepository;
import com.singleone.backend.project.ProjectRequestException;
import com.singleone.backend.project.ProjectService;

/**
 * PRD 10장 Media Planning Simulation 오케스트레이션. 추천/자동배분/최적화는 어디에도 없다
 * (Hard Rule 6) — 사용자가 입력한 매체별 예산 그대로에 대한 예측만 계산한다. 계산 자체는
 * {@code com.singleone.backend.analytics}의 기존 집계/필터율 로직을 재사용하고, 이번 단계에서
 * 새로 만드는 것은 주간 버킷화·회귀 적합·예산 범위 분류뿐이다.
 */
@Service
public class SimulationService {

	private static final MathContext MC = MathContext.DECIMAL128;
	private static final int CANDIDATE_WEEKS = 8;
	private static final BigDecimal MIN_VALID_PURCHASE_SUM = new BigDecimal("100");
	private static final BigDecimal MIN_VALID_COST_RATIO = new BigDecimal("1.2");
	private static final BigDecimal HIGH_CONFIDENCE_COST_RATIO = new BigDecimal("1.3");
	private static final BigDecimal HIGH_CONFIDENCE_R_SQUARED = new BigDecimal("0.75");
	private static final BigDecimal EXTRAPOLATION_LIMIT_RATIO = new BigDecimal("1.5");
	private static final BigDecimal SEVEN = new BigDecimal(7);
	private static final int CURVE_POINT_COUNT = 24;

	private final ProjectRepository projectRepository;
	private final ProjectService projectService;
	private final PerformanceAggregationRepository aggregationRepository;
	private final InternalMediaFilterRepository filterRepository;
	private final SingleOneIndexCalculator calculator;
	private final WeeklyLogModelFitter fitter;

	public SimulationService(ProjectRepository projectRepository, ProjectService projectService,
			PerformanceAggregationRepository aggregationRepository, InternalMediaFilterRepository filterRepository,
			SingleOneIndexCalculator calculator, WeeklyLogModelFitter fitter) {
		this.projectRepository = projectRepository;
		this.projectService = projectService;
		this.aggregationRepository = aggregationRepository;
		this.filterRepository = filterRepository;
		this.calculator = calculator;
		this.fitter = fitter;
	}

	public SimulationResult simulate(Long projectId, SimulationRequest request) {
		Project project = getProjectOrThrow(projectId);
		// PRD 10.2/AC-22: 시스템 "전체 캠페인" 프로젝트는 참고 분석 전용이라 Simulation에서 선택할 수 없다.
		if (project.isSystemDefault()) {
			throw new ProjectRequestException("전체 캠페인 프로젝트는 Media Planning Simulation에서 선택할 수 없습니다.");
		}

		List<ProjectCampaignId> campaigns = projectService.resolveIncludedCampaigns(project);
		Set<Media> projectMedia = EnumSet.noneOf(Media.class);
		for (ProjectCampaignId id : campaigns) {
			projectMedia.add(id.getMedia());
		}

		LocalDate modelWindowStart = request.baseTo().minusDays(7L * CANDIDATE_WEEKS - 1);
		LocalDate fetchFrom = request.baseFrom().isBefore(modelWindowStart) ? request.baseFrom() : modelWindowStart;
		List<DailyMediaTotal> daily = aggregationRepository.fetchDailyMediaTotals(project.getAdvertiserId(), campaigns,
			fetchFrom, request.baseTo());

		long baseDays = ChronoUnit.DAYS.between(request.baseFrom(), request.baseTo()) + 1;
		long simDays = ChronoUnit.DAYS.between(request.simFrom(), request.simTo()) + 1;

		List<MediaSimulationResult> mediaResults = new ArrayList<>();
		BigDecimal totalBudget = BigDecimal.ZERO;
		BigDecimal totalPurchases = BigDecimal.ZERO;
		BigDecimal totalRevenue = BigDecimal.ZERO;
		boolean totalAvailable = true;

		for (Media media : projectMedia) {
			BigDecimal inputBudget = request.mediaBudgets().getOrDefault(media, BigDecimal.ZERO);
			totalBudget = totalBudget.add(inputBudget);

			MediaSimulationResult result = simulateMedia(daily, media, request, inputBudget, baseDays, simDays);
			mediaResults.add(result);

			if (inputBudget.signum() > 0) {
				if (result.confidence() == ConfidenceLevel.UNAVAILABLE) {
					totalAvailable = false;
				} else {
					totalPurchases = totalPurchases.add(result.predictedPurchases());
					totalRevenue = totalRevenue.add(result.predictedRevenue());
				}
			}
		}

		BigDecimal totalCpa = null;
		BigDecimal totalRoas = null;
		if (totalAvailable) {
			totalCpa = totalPurchases.signum() > 0 ? totalBudget.divide(totalPurchases, MC) : null;
			totalRoas = totalBudget.signum() > 0
				? totalRevenue.multiply(new BigDecimal(100), MC).divide(totalBudget, MC)
				: BigDecimal.ZERO;
		}

		return new SimulationResult(mediaResults, totalBudget, totalAvailable,
			totalAvailable ? totalPurchases : null, totalAvailable ? totalRevenue : null, totalCpa, totalRoas,
			SimulationResult.DISCLAIMER);
	}

	private MediaSimulationResult simulateMedia(List<DailyMediaTotal> daily, Media media,
			SimulationRequest request, BigDecimal inputBudget, long baseDays, long simDays) {
		BigDecimal filterRate = filterRepository.findById(media).orElseThrow().getFilterRate();

		BigDecimal baseCost = costFor(daily, media, request.baseFrom(), request.baseTo());
		BigDecimal convertedCurrentBudget = baseCost.divide(new BigDecimal(baseDays), MC).multiply(new BigDecimal(simDays), MC);
		BigDecimal convertedCurrentWeeklyBudget = convertedCurrentBudget.divide(new BigDecimal(simDays), MC).multiply(SEVEN, MC);

		List<BigDecimal> weeklyCosts = new ArrayList<>();
		List<BigDecimal> weeklyPurchases = new ArrayList<>();
		List<BigDecimal> weeklyRevenues = new ArrayList<>();
		for (int i = 0; i < CANDIDATE_WEEKS; i++) {
			LocalDate weekEnd = request.baseTo().minusDays(7L * i);
			LocalDate weekStart = weekEnd.minusDays(6);
			MediaPerformanceTotals weekTotals = calculator.aggregateWindow(daily, EnumSet.of(media), weekStart, weekEnd).get(media);
			if (weekTotals == null) {
				continue; // 해당 주에 데이터 자체가 없음 -> 필수 원본 필드 결측, 무효 주차(PRD 10.5)
			}
			if (weekTotals.cost().signum() <= 0 || weekTotals.impressions().signum() <= 0 || weekTotals.clicks().signum() <= 0) {
				continue;
			}
			SingleOnePerformance perf = calculator.computeSingleOnePerformance(weekTotals, filterRate);
			if (perf.singleOnePurchases().signum() <= 0 || perf.singleOneRevenue().signum() < 0) {
				continue;
			}
			weeklyCosts.add(weekTotals.cost());
			weeklyPurchases.add(perf.singleOnePurchases());
			weeklyRevenues.add(perf.singleOneRevenue());
		}

		int validWeekCount = weeklyCosts.size();
		BigDecimal purchaseSum = weeklyPurchases.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal minCost = weeklyCosts.stream().min(BigDecimal::compareTo).orElse(null);
		BigDecimal maxCost = weeklyCosts.stream().max(BigDecimal::compareTo).orElse(null);
		BigDecimal costRatio = (minCost != null && minCost.signum() > 0) ? maxCost.divide(minCost, MC) : BigDecimal.ZERO;

		// PRD 10.5/AC-47: 유효 주차 수/SingleONE 구매 합계/비용 변동폭은 두 모델(구매·매출)에
		// 공통으로 적용되는 주차 집합 자체의 조건이다.
		boolean weekSetValid = validWeekCount >= 6 && purchaseSum.compareTo(MIN_VALID_PURCHASE_SUM) >= 0
			&& costRatio.compareTo(MIN_VALID_COST_RATIO) >= 0;

		LogModel purchaseModel = weekSetValid ? fitter.fit(weeklyCosts, weeklyPurchases) : LogModel.INVALID;
		LogModel revenueModel = weekSetValid ? fitter.fit(weeklyCosts, weeklyRevenues) : LogModel.INVALID;
		boolean modelValid = weekSetValid && purchaseModel.valid() && revenueModel.valid();

		if (inputBudget.signum() == 0) {
			// PRD 10.7: 입력 예산 0은 신뢰도 등급 자체가 해당 없음 -> null.
			return new MediaSimulationResult(media, inputBudget, BigDecimal.ZERO, convertedCurrentBudget,
				convertedCurrentWeeklyBudget, null, BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO,
				minCost, maxCost, List.of(), List.of());
		}

		BigDecimal weeklyBudget = inputBudget.divide(new BigDecimal(simDays), MC).multiply(SEVEN, MC);

		if (!modelValid) {
			return new MediaSimulationResult(media, inputBudget, weeklyBudget, convertedCurrentBudget,
				convertedCurrentWeeklyBudget, ConfidenceLevel.UNAVAILABLE, null, null, null, null, minCost, maxCost,
				List.of(), List.of("데이터 부족"));
		}

		BigDecimal extrapolationLimit = maxCost.multiply(EXTRAPOLATION_LIMIT_RATIO, MC);
		if (weeklyBudget.compareTo(extrapolationLimit) > 0) {
			return new MediaSimulationResult(media, inputBudget, weeklyBudget, convertedCurrentBudget,
				convertedCurrentWeeklyBudget, ConfidenceLevel.UNAVAILABLE, null, null, null, null, minCost, maxCost,
				List.of(), List.of("과거 운영 범위 초과", "포화구간 진입 가능성"));
		}

		BigDecimal weeklyPredictedPurchases = fitter.predict(purchaseModel, weeklyBudget);
		BigDecimal weeklyPredictedRevenue = fitter.predict(revenueModel, weeklyBudget);
		BigDecimal periodPurchases = weeklyPredictedPurchases.multiply(new BigDecimal(simDays), MC).divide(SEVEN, MC);
		BigDecimal periodRevenue = weeklyPredictedRevenue.multiply(new BigDecimal(simDays), MC).divide(SEVEN, MC);

		BigDecimal cpa = periodPurchases.signum() > 0 ? inputBudget.divide(periodPurchases, MC) : null;
		BigDecimal roas = periodPurchases.signum() > 0
			? periodRevenue.multiply(new BigDecimal(100), MC).divide(inputBudget, MC)
			: BigDecimal.ZERO;

		List<String> notes = new ArrayList<>();
		ConfidenceLevel confidence;
		if (weeklyBudget.compareTo(minCost) < 0) {
			confidence = ConfidenceLevel.LOW;
			notes.add("과거 운영 범위 미만");
			notes.add("데이터 부족");
		} else if (weeklyBudget.compareTo(maxCost) > 0) {
			confidence = ConfidenceLevel.LOW;
			notes.add("과거 운영 범위 초과");
			notes.add("포화구간 진입 가능성");
		} else {
			BigDecimal minRSquared = purchaseModel.rSquared().min(revenueModel.rSquared());
			boolean high = minRSquared.compareTo(HIGH_CONFIDENCE_R_SQUARED) >= 0 && validWeekCount == CANDIDATE_WEEKS
				&& costRatio.compareTo(HIGH_CONFIDENCE_COST_RATIO) >= 0;
			confidence = high ? ConfidenceLevel.HIGH : ConfidenceLevel.MEDIUM;
			notes.add("효율 감소 관찰");
		}

		List<CurvePoint> curvePoints = buildCurve(purchaseModel, revenueModel, minCost, maxCost);

		return new MediaSimulationResult(media, inputBudget, weeklyBudget, convertedCurrentBudget,
			convertedCurrentWeeklyBudget, confidence, periodPurchases, periodRevenue, cpa, roas, minCost, maxCost,
			curvePoints, notes);
	}

	private List<CurvePoint> buildCurve(LogModel purchaseModel, LogModel revenueModel, BigDecimal minCost, BigDecimal maxCost) {
		BigDecimal domainStart = minCost.multiply(new BigDecimal("0.1"), MC);
		BigDecimal domainEnd = maxCost.multiply(EXTRAPOLATION_LIMIT_RATIO, MC);
		BigDecimal step = domainEnd.subtract(domainStart, MC).divide(new BigDecimal(CURVE_POINT_COUNT - 1), MC);

		List<CurvePoint> points = new ArrayList<>();
		BigDecimal x = domainStart;
		for (int i = 0; i < CURVE_POINT_COUNT; i++) {
			points.add(new CurvePoint(x, fitter.predict(purchaseModel, x), fitter.predict(revenueModel, x)));
			x = x.add(step, MC);
		}
		return points;
	}

	private BigDecimal costFor(List<DailyMediaTotal> daily, Media media, LocalDate from, LocalDate to) {
		MediaPerformanceTotals totals = calculator.aggregateWindow(daily, EnumSet.of(media), from, to).get(media);
		return totals == null ? BigDecimal.ZERO : totals.cost();
	}

	private Project getProjectOrThrow(Long projectId) {
		return projectRepository.findById(projectId)
			.orElseThrow(() -> new ProjectRequestException("존재하지 않는 프로젝트입니다: " + projectId));
	}

}
