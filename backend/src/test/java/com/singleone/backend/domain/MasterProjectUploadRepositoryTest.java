package com.singleone.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.singleone.backend.AbstractIntegrationTest;
import com.singleone.backend.domain.advertiser.Advertiser;
import com.singleone.backend.domain.advertiser.AdvertiserRepository;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.filter.InternalMediaFilterRepository;
import com.singleone.backend.domain.master.AdGroupMaster;
import com.singleone.backend.domain.master.AdGroupMasterId;
import com.singleone.backend.domain.master.AdGroupMasterRepository;
import com.singleone.backend.domain.master.AdMaster;
import com.singleone.backend.domain.master.AdMasterId;
import com.singleone.backend.domain.master.AdMasterRepository;
import com.singleone.backend.domain.master.CampaignMaster;
import com.singleone.backend.domain.master.CampaignMasterId;
import com.singleone.backend.domain.master.CampaignMasterRepository;
import com.singleone.backend.domain.project.Project;
import com.singleone.backend.domain.project.ProjectCampaign;
import com.singleone.backend.domain.project.ProjectCampaignId;
import com.singleone.backend.domain.project.ProjectRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * PRD 12장 논리 데이터 모델과 5.4 식별 규칙, 8.3 필터율 시드값을 검증한다.
 */
class MasterProjectUploadRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private AdvertiserRepository advertiserRepository;

	@Autowired
	private CampaignMasterRepository campaignMasterRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private InternalMediaFilterRepository internalMediaFilterRepository;

	@Autowired
	private AdGroupMasterRepository adGroupMasterRepository;

	@Autowired
	private AdMasterRepository adMasterRepository;

	@Autowired
	private com.singleone.backend.domain.project.ProjectCampaignRepository projectCampaignRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void campaignMasterPersistsWithCompositeNaturalKey() {
		advertiserRepository.save(new Advertiser("adv-1", "ABC Brand"));

		CampaignMasterId id = new CampaignMasterId("adv-1", Media.META, "camp-1");
		campaignMasterRepository.save(new CampaignMaster(id, "여름 프로모션"));

		CampaignMaster found = campaignMasterRepository.findById(id).orElseThrow();
		assertThat(found.getLatestName()).isEqualTo("여름 프로모션");
		assertThat(found.getId().getMedia()).isEqualTo(Media.META);
	}

	@Test
	void projectNameMustBeUniquePerAdvertiser() {
		advertiserRepository.save(new Advertiser("adv-2", "XYZ Brand"));
		projectRepository.saveAndFlush(new Project("adv-2", "전체 비교", false, false));

		assertThatThrownBy(() ->
			projectRepository.saveAndFlush(new Project("adv-2", "전체 비교", false, false))
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void internalMediaFilterSeedMatchesPrd() {
		List<Object[]> expected = List.of(
			new Object[] {Media.META, new BigDecimal("0.6500")},
			new Object[] {Media.TIKTOK, new BigDecimal("0.6200")},
			new Object[] {Media.GOOGLE, new BigDecimal("0.6900")},
			new Object[] {Media.NAVER, new BigDecimal("0.6400")},
			new Object[] {Media.CRITEO, new BigDecimal("0.6100")}
		);

		assertThat(internalMediaFilterRepository.findAll()).hasSize(5);
		for (Object[] row : expected) {
			Media media = (Media) row[0];
			BigDecimal rate = (BigDecimal) row[1];
			assertThat(internalMediaFilterRepository.findById(media).orElseThrow().getFilterRate())
				.isEqualByComparingTo(rate);
		}
	}

	@Test
	void adGroupAndAdMasterPersistWithCompositeNaturalKey() {
		advertiserRepository.save(new Advertiser("adv-3", "Ad Group Brand"));
		CampaignMasterId campaignId = new CampaignMasterId("adv-3", Media.GOOGLE, "camp-3");
		campaignMasterRepository.save(new CampaignMaster(campaignId, "캠페인3"));

		AdGroupMasterId adGroupId = new AdGroupMasterId("adv-3", Media.GOOGLE, "camp-3", "ag-3");
		adGroupMasterRepository.save(new AdGroupMaster(adGroupId, "광고그룹3"));

		AdMasterId adId = new AdMasterId("adv-3", Media.GOOGLE, "camp-3", "ag-3", "ad-3");
		adMasterRepository.save(new AdMaster(adId, "광고3"));

		assertThat(adGroupMasterRepository.findById(adGroupId).orElseThrow().getLatestName()).isEqualTo("광고그룹3");
		assertThat(adMasterRepository.findById(adId).orElseThrow().getLatestName()).isEqualTo("광고3");
	}

	@Test
	void projectCampaignAllowsSameCampaignAcrossDifferentProjectsButNotWithinSameProject() {
		advertiserRepository.save(new Advertiser("adv-4", "Project Brand"));
		CampaignMasterId campaignId = new CampaignMasterId("adv-4", Media.META, "camp-4");
		campaignMasterRepository.save(new CampaignMaster(campaignId, "캠페인4"));

		Project projectA = projectRepository.saveAndFlush(new Project("adv-4", "프로젝트 A", false, false));
		Project projectB = projectRepository.saveAndFlush(new Project("adv-4", "프로젝트 B", false, false));

		ProjectCampaignId idInA = new ProjectCampaignId(projectA.getProjectId(), "adv-4", Media.META, "camp-4");
		entityManager.persist(new ProjectCampaign(idInA));
		entityManager.flush();

		// PRD 5.1: 동일 캠페인은 다른 프로젝트에는 중복 포함 가능하다.
		ProjectCampaignId idInB = new ProjectCampaignId(projectB.getProjectId(), "adv-4", Media.META, "camp-4");
		entityManager.persist(new ProjectCampaign(idInB));
		entityManager.flush();
		assertThat(projectCampaignRepository.findById(idInB)).isPresent();

		// PRD 5.1: 동일 캠페인은 같은 프로젝트 안에서 한 번만 선택 가능하다 (PK 위반이어야 한다).
		assertThatThrownBy(() -> {
			entityManager.persist(new ProjectCampaign(idInA));
			entityManager.flush();
		}).isInstanceOf(PersistenceException.class);
	}

	@Test
	void adGroupMasterRequiresExistingCampaignMaster() {
		advertiserRepository.save(new Advertiser("adv-5", "FK Brand"));
		// campaign_master에 존재하지 않는 campaign_id를 참조 → FK 위반으로 실패해야 한다.
		AdGroupMasterId orphanId = new AdGroupMasterId("adv-5", Media.META, "no-such-campaign", "ag-5");

		assertThatThrownBy(() ->
			adGroupMasterRepository.saveAndFlush(new AdGroupMaster(orphanId, "고아 광고그룹"))
		).isInstanceOf(DataIntegrityViolationException.class);
	}

}
