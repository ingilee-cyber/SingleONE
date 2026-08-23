package com.singleone.backend.dashboard;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** PRD 6장 Dashboard API. */
@RestController
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/api/v1/projects/{projectId}/dashboard")
	public DashboardResponse getDashboard(@PathVariable Long projectId,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return dashboardService.getDashboard(projectId, from, to);
	}

}
