package com.singleone.backend.detail;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.singleone.backend.domain.common.Media;

/** PRD 7장 매체/캠페인/광고그룹/광고 상세 API. */
@RestController
public class DetailController {

	private static final int DEFAULT_PAGE_SIZE = 50;
	private static final int MAX_PAGE_SIZE = 200;

	private final DetailService detailService;

	public DetailController(DetailService detailService) {
		this.detailService = detailService;
	}

	@GetMapping("/api/v1/projects/{projectId}/media/{media}/summary")
	public MediaDetailResponse getMediaDetail(@PathVariable Long projectId, @PathVariable Media media,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return detailService.getMediaDetail(projectId, media, from, to);
	}

	@GetMapping("/api/v1/projects/{projectId}/media/{media}/campaigns")
	public Page<EntityPerformance> listCampaigns(@PathVariable Long projectId, @PathVariable Media media,
			@RequestParam LocalDate from, @RequestParam LocalDate to,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
			@RequestParam(required = false) String sort) {
		return detailService.listCampaigns(projectId, media, from, to, search, pageable(page, size, sort));
	}

	@GetMapping("/api/v1/projects/{projectId}/media/{media}/campaigns/{campaignId}/summary")
	public EntityPerformanceComparison getCampaignDetail(@PathVariable Long projectId, @PathVariable Media media,
			@PathVariable String campaignId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
		return detailService.getCampaignDetail(projectId, media, campaignId, from, to);
	}

	@GetMapping("/api/v1/projects/{projectId}/media/{media}/campaigns/{campaignId}/ad-groups")
	public Page<EntityPerformance> listAdGroups(@PathVariable Long projectId, @PathVariable Media media,
			@PathVariable String campaignId, @RequestParam LocalDate from, @RequestParam LocalDate to,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
			@RequestParam(required = false) String sort) {
		return detailService.listAdGroups(projectId, media, campaignId, from, to, search, pageable(page, size, sort));
	}

	@GetMapping("/api/v1/projects/{projectId}/media/{media}/campaigns/{campaignId}/ad-groups/{adGroupId}/summary")
	public EntityPerformance getAdGroupDetail(@PathVariable Long projectId, @PathVariable Media media,
			@PathVariable String campaignId, @PathVariable String adGroupId,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return detailService.getAdGroupDetail(projectId, media, campaignId, adGroupId, from, to);
	}

	@GetMapping("/api/v1/projects/{projectId}/media/{media}/campaigns/{campaignId}/ad-groups/{adGroupId}/ads")
	public Page<EntityPerformance> listAds(@PathVariable Long projectId, @PathVariable Media media,
			@PathVariable String campaignId, @PathVariable String adGroupId,
			@RequestParam LocalDate from, @RequestParam LocalDate to,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
			@RequestParam(required = false) String sort) {
		return detailService.listAds(projectId, media, campaignId, adGroupId, from, to, search, pageable(page, size, sort));
	}

	@GetMapping("/api/v1/projects/{projectId}/media/{media}/campaigns/{campaignId}/ad-groups/{adGroupId}/ads/{adId}/summary")
	public EntityPerformance getAdDetail(@PathVariable Long projectId, @PathVariable Media media,
			@PathVariable String campaignId, @PathVariable String adGroupId, @PathVariable String adId,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return detailService.getAdDetail(projectId, media, campaignId, adGroupId, adId, from, to);
	}

	private Pageable pageable(int page, int size, String sort) {
		int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		if (sort == null || sort.isBlank()) {
			return PageRequest.of(page, clampedSize, Sort.by("name").ascending());
		}
		String[] parts = sort.split(",");
		Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]) ? Sort.Direction.DESC : Sort.Direction.ASC;
		return PageRequest.of(page, clampedSize, Sort.by(direction, parts[0]));
	}

}
