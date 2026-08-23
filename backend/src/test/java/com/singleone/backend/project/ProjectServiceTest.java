package com.singleone.backend.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.singleone.backend.AbstractIntegrationTest;
import com.singleone.backend.domain.advertiser.Advertiser;
import com.singleone.backend.domain.advertiser.AdvertiserRepository;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.master.CampaignMaster;
import com.singleone.backend.domain.master.CampaignMasterId;
import com.singleone.backend.domain.master.CampaignMasterRepository;
import com.singleone.backend.domain.project.Project;
import com.singleone.backend.domain.project.ProjectCampaignId;
import com.singleone.backend.domain.project.ProjectCampaignRepository;
import com.singleone.backend.domain.project.ProjectRepository;

/**
 * PRD 5장 프로젝트 요구사항, AC-17~AC-21 검증.
 */
class ProjectServiceTest extends AbstractIntegrationTest {

	@Autowired
	private ProjectService projectService;

	@Autowired
	private AdvertiserRepository advertiserRepository;

	@Autowired
	private CampaignMasterRepository campaignMasterRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectCampaignRepository projectCampaignRepository;

	private void campaign(String advertiserId, Media media, String campaignId) {
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, media, campaignId), campaignId + "-name"));
	}

	private ProjectUpsertRequest request(String name, CampaignSelection... campaigns) {
		return new ProjectUpsertRequest(name, List.of(campaigns));
	}

	@Test
	void createProjectSucceedsWithTwoMediaAndCampaigns() {
		String advertiserId = "adv-proj-1";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주1"));
		campaign(advertiserId, Media.META, "camp-meta");
		campaign(advertiserId, Media.GOOGLE, "camp-google");

		Project project = projectService.createProject(advertiserId,
			request("정상 프로젝트", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.GOOGLE, "camp-google")));

		assertThat(project.getProjectId()).isNotNull();
		ProjectResponse response = projectService.toResponse(project);
		assertThat(response.campaigns()).hasSize(2);
		assertThat(response.systemDefault()).isFalse();
	}

	@Test
	void duplicateProjectNameRejected() {
		String advertiserId = "adv-proj-2";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주2"));
		campaign(advertiserId, Media.META, "camp-meta");
		campaign(advertiserId, Media.GOOGLE, "camp-google");
		projectService.createProject(advertiserId,
			request("중복이름", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.GOOGLE, "camp-google")));

		assertThatThrownBy(() -> projectService.createProject(advertiserId,
			request("중복이름", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.GOOGLE, "camp-google"))))
			.isInstanceOf(ProjectRequestException.class)
			.hasMessageContaining("이미 사용 중인 프로젝트명");
	}

	// AC-17
	@Test
	void singleMediaRejected() {
		String advertiserId = "adv-proj-3";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주3"));
		campaign(advertiserId, Media.META, "camp-1");
		campaign(advertiserId, Media.META, "camp-2");

		assertThatThrownBy(() -> projectService.createProject(advertiserId,
			request("매체하나", new CampaignSelection(Media.META, "camp-1"), new CampaignSelection(Media.META, "camp-2"))))
			.isInstanceOf(ProjectRequestException.class)
			.hasMessageContaining("매체가 최소 2개");
	}

	// AC-18
	@Test
	void duplicateCampaignSelectionRejected() {
		String advertiserId = "adv-proj-4";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주4"));
		campaign(advertiserId, Media.META, "camp-meta");
		campaign(advertiserId, Media.GOOGLE, "camp-google");

		assertThatThrownBy(() -> projectService.createProject(advertiserId,
			request("중복캠페인", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.META, "camp-meta"),
				new CampaignSelection(Media.GOOGLE, "camp-google"))))
			.isInstanceOf(ProjectRequestException.class)
			.hasMessageContaining("중복 선택");
	}

	// AC-19
	@Test
	void sameCampaignReusableAcrossDifferentProjects() {
		String advertiserId = "adv-proj-5";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주5"));
		campaign(advertiserId, Media.META, "camp-meta");
		campaign(advertiserId, Media.GOOGLE, "camp-google");

		Project first = projectService.createProject(advertiserId,
			request("프로젝트A", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.GOOGLE, "camp-google")));
		Project second = projectService.createProject(advertiserId,
			request("프로젝트B", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.GOOGLE, "camp-google")));

		assertThat(first.getProjectId()).isNotEqualTo(second.getProjectId());
		assertThat(projectService.toResponse(second).campaigns()).hasSize(2);
	}

	@Test
	void nonexistentCampaignRejected() {
		String advertiserId = "adv-proj-6";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주6"));
		campaign(advertiserId, Media.META, "camp-meta");

		assertThatThrownBy(() -> projectService.createProject(advertiserId,
			request("없는캠페인", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.GOOGLE, "no-such-campaign"))))
			.isInstanceOf(ProjectRequestException.class)
			.hasMessageContaining("존재하지 않는 캠페인");
	}

	// PRD 5.2
	@Test
	void systemDefaultProjectAutoCreatedAndIncludesAllCampaignsDynamically() {
		String advertiserId = "adv-proj-7";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주7"));
		campaign(advertiserId, Media.META, "camp-1");
		campaign(advertiserId, Media.GOOGLE, "camp-2");

		Page<Project> page = projectService.listProjects(advertiserId, null, PageRequest.of(0, 50, Sort.by("projectName")));
		Project defaultProject = page.getContent().stream().filter(Project::isSystemDefault).findFirst().orElseThrow();
		assertThat(defaultProject.isReferenceOnly()).isTrue();
		assertThat(projectService.toResponse(defaultProject).campaigns()).hasSize(2);

		// 새 캠페인이 업로드로 추가되면 별도 동기화 없이도 전체 캠페인 프로젝트에 자동 반영된다.
		campaign(advertiserId, Media.NAVER, "camp-3");
		assertThat(projectService.toResponse(defaultProject).campaigns()).hasSize(3);
	}

	// AC-21
	@Test
	void systemDefaultProjectCannotBeUpdatedOrDeleted() {
		String advertiserId = "adv-proj-8";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주8"));
		campaign(advertiserId, Media.META, "camp-1");

		projectService.listProjects(advertiserId, null, PageRequest.of(0, 50, Sort.by("projectName")));
		Project defaultProject = projectRepository.findByAdvertiserIdAndSystemDefaultTrue(advertiserId).orElseThrow();

		assertThatThrownBy(() -> projectService.updateProject(defaultProject.getProjectId(), request("이름변경")))
			.isInstanceOf(ProjectRequestException.class)
			.hasMessageContaining("수정할 수 없습니다");
		assertThatThrownBy(() -> projectService.deleteProject(defaultProject.getProjectId()))
			.isInstanceOf(ProjectRequestException.class)
			.hasMessageContaining("삭제할 수 없습니다");
	}

	@Test
	void deletingProjectCascadesProjectCampaignRows() {
		String advertiserId = "adv-proj-9";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주9"));
		campaign(advertiserId, Media.META, "camp-meta");
		campaign(advertiserId, Media.GOOGLE, "camp-google");
		Project project = projectService.createProject(advertiserId,
			request("삭제될프로젝트", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.GOOGLE, "camp-google")));

		projectService.deleteProject(project.getProjectId());

		assertThat(projectRepository.findById(project.getProjectId())).isEmpty();
		assertThat(projectCampaignRepository.findByIdProjectId(project.getProjectId())).isEmpty();
	}

	@Test
	void updateProjectKeepsIdAndReplacesCampaigns() {
		String advertiserId = "adv-proj-10";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주10"));
		campaign(advertiserId, Media.META, "camp-meta");
		campaign(advertiserId, Media.GOOGLE, "camp-google");
		campaign(advertiserId, Media.NAVER, "camp-naver");
		Project project = projectService.createProject(advertiserId,
			request("수정전", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.GOOGLE, "camp-google")));
		Long originalId = project.getProjectId();

		Project updated = projectService.updateProject(originalId,
			request("수정후", new CampaignSelection(Media.GOOGLE, "camp-google"), new CampaignSelection(Media.NAVER, "camp-naver")));

		assertThat(updated.getProjectId()).isEqualTo(originalId);
		assertThat(updated.getProjectName()).isEqualTo("수정후");
		List<ProjectCampaignId> ids = projectCampaignRepository.findByIdProjectId(originalId).stream()
			.map(pc -> pc.getId()).toList();
		assertThat(ids).hasSize(2);
		assertThat(ids).noneMatch(id -> id.getMedia() == Media.META);
	}

	@Test
	void listProjectsSupportsSearchAndClampsPageSizeAtTwoHundred() {
		String advertiserId = "adv-proj-11";
		advertiserRepository.save(new Advertiser(advertiserId, "프로젝트광고주11"));
		campaign(advertiserId, Media.META, "camp-meta");
		campaign(advertiserId, Media.GOOGLE, "camp-google");
		projectService.createProject(advertiserId,
			request("여름캠페인프로젝트", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.GOOGLE, "camp-google")));
		projectService.createProject(advertiserId,
			request("겨울캠페인프로젝트", new CampaignSelection(Media.META, "camp-meta"), new CampaignSelection(Media.GOOGLE, "camp-google")));

		Page<Project> searched = projectService.listProjects(advertiserId, "여름", PageRequest.of(0, 50, Sort.by("projectName")));
		assertThat(searched.getContent()).extracting(Project::getProjectName).containsExactly("여름캠페인프로젝트");

		Page<Project> all = projectService.listProjects(advertiserId, null, PageRequest.of(0, 50, Sort.by("projectName")));
		// 시스템 기본(전체 캠페인) 프로젝트 1개 + 생성한 2개 = 3개
		assertThat(all.getContent()).hasSize(3);
	}

}
