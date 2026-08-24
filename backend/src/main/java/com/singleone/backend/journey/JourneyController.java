package com.singleone.backend.journey;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** PRD 9장 Journey & Attribution API. Dashboard 요약과 Journey 화면 3개 탭이 이 응답 하나를 공유한다. */
@RestController
public class JourneyController {

	private final JourneyAnalysisService journeyAnalysisService;

	public JourneyController(JourneyAnalysisService journeyAnalysisService) {
		this.journeyAnalysisService = journeyAnalysisService;
	}

	@GetMapping("/api/v1/projects/{projectId}/journey")
	public JourneyAnalysisResult getJourney(@PathVariable Long projectId,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return journeyAnalysisService.getJourneyAnalysis(projectId, from, to);
	}

}
