package com.singleone.backend.project;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.singleone.backend.domain.advertiser.AdvertiserRepository;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.master.CampaignMaster;
import com.singleone.backend.domain.master.CampaignMasterId;
import com.singleone.backend.domain.master.CampaignMasterRepository;
import com.singleone.backend.domain.project.Project;
import com.singleone.backend.domain.project.ProjectCampaign;
import com.singleone.backend.domain.project.ProjectCampaignId;
import com.singleone.backend.domain.project.ProjectCampaignRepository;
import com.singleone.backend.domain.project.ProjectRepository;

/**
 * PRD 5장 프로젝트 요구사항, AC-17~AC-22. "전체 캠페인" 시스템 기본 프로젝트(PRD 5.2)는
 * project 행 1개만 저장하고, 포함 캠페인은 저장하지 않고 그 광고주의 campaign_master 전체를
 * 항상 동적으로 계산한다(업로드로 캠페인이 늘어나도 별도 동기화가 필요 없음).
 */
@Service
public class ProjectService {

	private static final String DEFAULT_PROJECT_NAME = "전체 캠페인";
	private static final int MIN_DISTINCT_MEDIA = 2;

	private final ProjectRepository projectRepository;
	private final ProjectCampaignRepository projectCampaignRepository;
	private final CampaignMasterRepository campaignMasterRepository;
	private final AdvertiserRepository advertiserRepository;

	public ProjectService(ProjectRepository projectRepository, ProjectCampaignRepository projectCampaignRepository,
			CampaignMasterRepository campaignMasterRepository, AdvertiserRepository advertiserRepository) {
		this.projectRepository = projectRepository;
		this.projectCampaignRepository = projectCampaignRepository;
		this.campaignMasterRepository = campaignMasterRepository;
		this.advertiserRepository = advertiserRepository;
	}

	@Transactional
	public Page<Project> listProjects(String advertiserId, String search, Pageable pageable) {
		if (!advertiserRepository.existsById(advertiserId)) {
			return Page.empty(pageable);
		}
		ensureDefaultProject(advertiserId);
		return projectRepository.search(advertiserId, blankToNull(search), pageable);
	}

	private void ensureDefaultProject(String advertiserId) {
		if (projectRepository.findByAdvertiserIdAndSystemDefaultTrue(advertiserId).isEmpty()) {
			projectRepository.saveAndFlush(new Project(advertiserId, DEFAULT_PROJECT_NAME, true, true));
		}
	}

	@Transactional
	public Project createProject(String advertiserId, ProjectUpsertRequest request) {
		if (!advertiserRepository.existsById(advertiserId)) {
			throw new ProjectRequestException("존재하지 않는 광고주입니다: " + advertiserId);
		}
		String name = validateAndNormalizeName(advertiserId, request, null);
		validateCampaigns(advertiserId, request.campaigns());

		Project project = projectRepository.saveAndFlush(new Project(advertiserId, name, false, false));
		saveCampaigns(project, advertiserId, request.campaigns());
		return project;
	}

	/** PRD 5.1: 수정 시 project_id는 유지하고 캠페인 구성은 전체 교체한다. */
	@Transactional
	public Project updateProject(Long projectId, ProjectUpsertRequest request) {
		Project project = getProjectOrThrow(projectId);
		if (project.isSystemDefault()) {
			throw new ProjectRequestException("시스템 기본 프로젝트는 수정할 수 없습니다.");
		}
		String name = validateAndNormalizeName(project.getAdvertiserId(), request, project.getProjectName());
		validateCampaigns(project.getAdvertiserId(), request.campaigns());

		project.setProjectName(name);
		projectRepository.saveAndFlush(project);
		projectCampaignRepository.deleteAll(projectCampaignRepository.findByIdProjectId(projectId));
		saveCampaigns(project, project.getAdvertiserId(), request.campaigns());
		return project;
	}

	@Transactional
	public void deleteProject(Long projectId) {
		Project project = getProjectOrThrow(projectId);
		if (project.isSystemDefault()) {
			throw new ProjectRequestException("시스템 기본 프로젝트는 삭제할 수 없습니다.");
		}
		projectRepository.delete(project);
	}

	public Page<CampaignMaster> searchCampaigns(String advertiserId, String search, Media media, Pageable pageable) {
		return campaignMasterRepository.search(advertiserId, media, blankToNull(search), pageable);
	}

	/** Stage 3 SingleOnePerformanceService 등 프로젝트의 실제 포함 캠페인이 필요한 곳에서 재사용한다. */
	public List<ProjectCampaignId> resolveIncludedCampaigns(Project project) {
		if (project.isSystemDefault()) {
			return campaignMasterRepository.findByIdAdvertiserId(project.getAdvertiserId()).stream()
				.map(cm -> new ProjectCampaignId(project.getProjectId(), cm.getId().getAdvertiserId(),
					cm.getId().getMedia(), cm.getId().getCampaignId()))
				.toList();
		}
		return projectCampaignRepository.findByIdProjectId(project.getProjectId()).stream()
			.map(ProjectCampaign::getId)
			.toList();
	}

	public ProjectResponse toResponse(Project project) {
		return new ProjectResponse(project.getProjectId(), project.getAdvertiserId(), project.getProjectName(),
			project.isSystemDefault(), project.isReferenceOnly(), toCampaignOptions(project),
			project.getCreatedAt(), project.getUpdatedAt());
	}

	private List<CampaignOptionResponse> toCampaignOptions(Project project) {
		List<CampaignMaster> allCampaigns = campaignMasterRepository.findByIdAdvertiserId(project.getAdvertiserId());
		if (project.isSystemDefault()) {
			return allCampaigns.stream()
				.map(cm -> new CampaignOptionResponse(cm.getId().getMedia(), cm.getId().getCampaignId(), cm.getLatestName()))
				.toList();
		}
		Set<String> selected = new HashSet<>();
		for (ProjectCampaign pc : projectCampaignRepository.findByIdProjectId(project.getProjectId())) {
			selected.add(pc.getId().getMedia() + "|" + pc.getId().getCampaignId());
		}
		return allCampaigns.stream()
			.filter(cm -> selected.contains(cm.getId().getMedia() + "|" + cm.getId().getCampaignId()))
			.map(cm -> new CampaignOptionResponse(cm.getId().getMedia(), cm.getId().getCampaignId(), cm.getLatestName()))
			.toList();
	}

	private String validateAndNormalizeName(String advertiserId, ProjectUpsertRequest request, String currentName) {
		String name = request.projectName() == null ? "" : request.projectName().trim();
		if (name.isEmpty()) {
			throw new ProjectRequestException("프로젝트명을 입력하세요.");
		}
		if (!name.equals(currentName) && projectRepository.existsByAdvertiserIdAndProjectName(advertiserId, name)) {
			throw new ProjectRequestException("이미 사용 중인 프로젝트명입니다: " + name);
		}
		return name;
	}

	/** PRD 5.1/AC-17/AC-18: 서로 다른 매체 2개 이상, 캠페인 중복 선택 금지, 실제 존재하는 캠페인이어야 함. */
	private void validateCampaigns(String advertiserId, List<CampaignSelection> campaigns) {
		List<CampaignSelection> selections = campaigns == null ? List.of() : campaigns;
		Set<String> distinctKeys = new HashSet<>();
		Set<Media> distinctMedia = EnumSet.noneOf(Media.class);
		for (CampaignSelection selection : selections) {
			String key = selection.media() + "|" + selection.campaignId();
			if (!distinctKeys.add(key)) {
				throw new ProjectRequestException("동일 캠페인을 중복 선택할 수 없습니다: " + selection.media() + " " + selection.campaignId());
			}
			distinctMedia.add(selection.media());
			CampaignMasterId id = new CampaignMasterId(advertiserId, selection.media(), selection.campaignId());
			if (!campaignMasterRepository.existsById(id)) {
				throw new ProjectRequestException("존재하지 않는 캠페인입니다: " + selection.media() + " " + selection.campaignId());
			}
		}
		if (distinctMedia.size() < MIN_DISTINCT_MEDIA) {
			throw new ProjectRequestException("서로 다른 매체가 최소 " + MIN_DISTINCT_MEDIA + "개 이상 포함되어야 합니다.");
		}
	}

	private void saveCampaigns(Project project, String advertiserId, List<CampaignSelection> campaigns) {
		List<ProjectCampaign> rows = campaigns.stream()
			.map(c -> new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, c.media(), c.campaignId())))
			.toList();
		projectCampaignRepository.saveAll(rows);
	}

	private Project getProjectOrThrow(Long projectId) {
		return projectRepository.findById(projectId)
			.orElseThrow(() -> new ProjectRequestException("존재하지 않는 프로젝트입니다: " + projectId));
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

}
