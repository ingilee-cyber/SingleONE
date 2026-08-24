package com.singleone.backend.journey;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.singleone.backend.common.time.TimeUtils;
import com.singleone.backend.domain.project.Project;
import com.singleone.backend.domain.project.ProjectRepository;
import com.singleone.backend.project.ProjectRequestException;
import com.singleone.backend.project.ProjectService;

/**
 * PRD 9장 Journey & Attribution 오케스트레이션. 프로젝트 검증 후 포함 캠페인을 구해
 * {@link JourneyEventRepository}로 이벤트를 가져오고 {@link JourneyAttributionCalculator}에
 * 계산을 위임한다({@code com.singleone.backend.detail.DetailService}와 동일한 패턴).
 */
@Service
public class JourneyAnalysisService {

	private final ProjectRepository projectRepository;
	private final ProjectService projectService;
	private final JourneyEventRepository journeyEventRepository;
	private final JourneyAttributionCalculator calculator;

	public JourneyAnalysisService(ProjectRepository projectRepository, ProjectService projectService,
			JourneyEventRepository journeyEventRepository, JourneyAttributionCalculator calculator) {
		this.projectRepository = projectRepository;
		this.projectService = projectService;
		this.journeyEventRepository = journeyEventRepository;
		this.calculator = calculator;
	}

	public JourneyAnalysisResult getJourneyAnalysis(Long projectId, LocalDate from, LocalDate to) {
		Project project = getProjectOrThrow(projectId);

		Set<String> eligibleCampaignKeys = projectService.resolveIncludedCampaigns(project).stream()
			.map(c -> c.getMedia().name() + "|" + c.getCampaignId())
			.collect(Collectors.toSet());

		// PRD 9.3: 구매 전 7일 + 직전 구매시점 규칙 모두 (from - 7일)보다 과거 데이터를 필요로
		// 하지 않는다 — [from, to] 내 구매의 유효 터치포인트 시작 시각은 항상 purchase_time - 7일
		// 이상이고, purchase_time >= from이므로 그 하한은 from - 7일보다 이르지 않다.
		Instant fetchFrom = TimeUtils.startOfDaySeoul(from.minusDays(7));
		Instant fetchTo = TimeUtils.endOfDaySeoul(to);
		List<JourneyEventRecord> events = journeyEventRepository.fetchEvents(project.getAdvertiserId(), fetchFrom, fetchTo);

		return calculator.analyze(events, eligibleCampaignKeys, from, to);
	}

	private Project getProjectOrThrow(Long projectId) {
		return projectRepository.findById(projectId)
			.orElseThrow(() -> new ProjectRequestException("존재하지 않는 프로젝트입니다: " + projectId));
	}

}
