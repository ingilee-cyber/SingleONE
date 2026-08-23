package com.singleone.backend.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.filter.InternalMediaFilter;
import com.singleone.backend.domain.filter.InternalMediaFilterRepository;
import com.singleone.backend.domain.project.Project;
import com.singleone.backend.domain.project.ProjectCampaignId;
import com.singleone.backend.domain.project.ProjectRepository;
import com.singleone.backend.project.ProjectRequestException;
import com.singleone.backend.project.ProjectService;

/**
 * PRD 8장 SingleONE 성과/Index 계산 오케스트레이션. 프로젝트의 캠페인 구성(MySQL, "전체 캠페인"
 * 시스템 기본 프로젝트 포함 — {@link ProjectService#resolveIncludedCampaigns})과 원본 성과
 * (ClickHouse)를 조회해 {@link SingleOneIndexCalculator}에 위임한다. advertiserId는 항상
 * 프로젝트 엔티티에서 읽어 호출자가 projectId와 어긋나는 advertiserId를 넘길 수 없게 한다.
 */
@Service
public class SingleOnePerformanceService {

	private final ProjectRepository projectRepository;
	private final ProjectService projectService;
	private final PerformanceAggregationRepository aggregationRepository;
	private final InternalMediaFilterRepository filterRepository;
	private final SingleOneIndexCalculator calculator;

	public SingleOnePerformanceService(ProjectRepository projectRepository, ProjectService projectService,
			PerformanceAggregationRepository aggregationRepository,
			InternalMediaFilterRepository filterRepository,
			SingleOneIndexCalculator calculator) {
		this.projectRepository = projectRepository;
		this.projectService = projectService;
		this.aggregationRepository = aggregationRepository;
		this.filterRepository = filterRepository;
		this.calculator = calculator;
	}

	/** PRD 8.4/8.5: 선택 프로젝트·기간의 매체별 SingleONE 성과/Index. */
	public List<MediaIndexResult> calculatePeriod(Long projectId, LocalDate from, LocalDate to) {
		Project project = getProjectOrThrow(projectId);
		List<ProjectCampaignId> campaigns = projectService.resolveIncludedCampaigns(project);
		Set<Media> projectMedia = projectMedia(campaigns);

		List<DailyMediaTotal> daily = aggregationRepository.fetchDailyMediaTotals(project.getAdvertiserId(), campaigns, from, to);
		Map<Media, MediaPerformanceTotals> totals = calculator.aggregateWindow(daily, projectMedia, from, to);
		return calculator.calculateIndex(projectMedia, totals, filterRates());
	}

	/** PRD 8.8: 선택 기간과 동일한 길이의 바로 직전 기간을 비교 대상으로 사용한다. */
	public PeriodComparison calculatePeriodWithPreviousComparison(Long projectId, LocalDate from, LocalDate to) {
		List<MediaIndexResult> current = calculatePeriod(projectId, from, to);
		long lengthDays = ChronoUnit.DAYS.between(from, to) + 1;
		LocalDate previousTo = from.minusDays(1);
		LocalDate previousFrom = previousTo.minusDays(lengthDays - 1);
		List<MediaIndexResult> previous = calculatePeriod(projectId, previousFrom, previousTo);
		return new PeriodComparison(current, previous);
	}

	/**
	 * PRD 8.9: 날짜 D마다 [D-6, D] window를 집계해 Index를 계산한다. 선택 기간(from~to) 이전의
	 * 데이터도 window 계산에 사용하되, 결과는 선택 기간의 날짜만 포함한다. 유효 비교 매체가 1개
	 * 이하인 날짜는 결과에서 제외한다(PRD 8.9/AC-16).
	 */
	public List<RollingIndexPoint> calculateRollingIndex(Long projectId, LocalDate from, LocalDate to) {
		Project project = getProjectOrThrow(projectId);
		List<ProjectCampaignId> campaigns = projectService.resolveIncludedCampaigns(project);
		Set<Media> projectMedia = projectMedia(campaigns);

		LocalDate fetchFrom = from.minusDays(6);
		List<DailyMediaTotal> daily = aggregationRepository.fetchDailyMediaTotals(project.getAdvertiserId(), campaigns, fetchFrom, to);
		Map<Media, BigDecimal> filterRates = filterRates();

		List<RollingIndexPoint> points = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			LocalDate windowStart = date.minusDays(6);
			Map<Media, MediaPerformanceTotals> totals = calculator.aggregateWindow(daily, projectMedia, windowStart, date);
			List<MediaIndexResult> results = calculator.calculateIndex(projectMedia, totals, filterRates);
			long validCount = results.stream().filter(r -> r.status() == IndexStatus.VALID).count();
			if (validCount >= 2) {
				points.add(new RollingIndexPoint(date, results));
			}
		}
		return points;
	}

	private Project getProjectOrThrow(Long projectId) {
		return projectRepository.findById(projectId)
			.orElseThrow(() -> new ProjectRequestException("존재하지 않는 프로젝트입니다: " + projectId));
	}

	private Set<Media> projectMedia(List<ProjectCampaignId> campaigns) {
		Set<Media> media = EnumSet.noneOf(Media.class);
		for (ProjectCampaignId id : campaigns) {
			media.add(id.getMedia());
		}
		return media;
	}

	private Map<Media, BigDecimal> filterRates() {
		Map<Media, BigDecimal> rates = new EnumMap<>(Media.class);
		for (InternalMediaFilter filter : filterRepository.findAll()) {
			rates.put(filter.getMedia(), filter.getFilterRate());
		}
		return rates;
	}

}
