package com.singleone.backend.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.project.Project;

/**
 * PRD 5장 Project API. 페이지네이션은 UploadController와 동일하게 기본 50/최대 200으로 clamp한다.
 */
@RestController
public class ProjectController {

	private static final int DEFAULT_PAGE_SIZE = 50;
	private static final int MAX_PAGE_SIZE = 200;

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping("/api/v1/advertisers/{advertiserId}/projects")
	public Page<ProjectResponse> listProjects(
			@PathVariable String advertiserId,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
			@RequestParam(required = false) String sort) {
		Pageable pageable = PageRequest.of(page, clamp(size), resolveSort(sort, "projectName"));
		return projectService.listProjects(advertiserId, search, pageable).map(projectService::toResponse);
	}

	@PostMapping("/api/v1/advertisers/{advertiserId}/projects")
	public ResponseEntity<ProjectResponse> createProject(@PathVariable String advertiserId,
			@RequestBody ProjectUpsertRequest request) {
		Project project = projectService.createProject(advertiserId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(projectService.toResponse(project));
	}

	@PutMapping("/api/v1/projects/{projectId}")
	public ProjectResponse updateProject(@PathVariable Long projectId, @RequestBody ProjectUpsertRequest request) {
		return projectService.toResponse(projectService.updateProject(projectId, request));
	}

	@DeleteMapping("/api/v1/projects/{projectId}")
	public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
		projectService.deleteProject(projectId);
		return ResponseEntity.noContent().build();
	}

	/** PRD 5.3: 캠페인명/ID 검색 및 매체 필터를 제공하는 프로젝트 캠페인 선택 UI용 조회. */
	@GetMapping("/api/v1/advertisers/{advertiserId}/campaigns")
	public Page<CampaignOptionResponse> searchCampaigns(
			@PathVariable String advertiserId,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) Media media,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
			@RequestParam(required = false) String sort) {
		Pageable pageable = PageRequest.of(page, clamp(size), resolveSort(sort, "latestName"));
		return projectService.searchCampaigns(advertiserId, search, media, pageable)
			.map(cm -> new CampaignOptionResponse(cm.getId().getMedia(), cm.getId().getCampaignId(), cm.getLatestName()));
	}

	private int clamp(int size) {
		return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
	}

	private Sort resolveSort(String sort, String defaultProperty) {
		if (sort == null || sort.isBlank()) {
			return Sort.by(defaultProperty).ascending();
		}
		String[] parts = sort.split(",");
		Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
			? Sort.Direction.DESC : Sort.Direction.ASC;
		return Sort.by(direction, parts[0]);
	}

}
