package com.singleone.backend.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.singleone.backend.AbstractIntegrationTest;
import com.singleone.backend.domain.advertiser.Advertiser;
import com.singleone.backend.domain.advertiser.AdvertiserRepository;
import com.singleone.backend.domain.common.JourneyEventType;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.master.CampaignMaster;
import com.singleone.backend.domain.master.CampaignMasterId;
import com.singleone.backend.domain.master.CampaignMasterRepository;
import com.singleone.backend.domain.project.Project;
import com.singleone.backend.domain.project.ProjectCampaign;
import com.singleone.backend.domain.project.ProjectCampaignId;
import com.singleone.backend.domain.project.ProjectRepository;
import com.singleone.backend.domain.upload.UploadBatch;
import com.singleone.backend.domain.upload.UploadBatchRepository;
import com.singleone.backend.domain.upload.UploadStatus;
import com.singleone.backend.domain.upload.UploadType;
import com.singleone.backend.project.ProjectRequestException;
import com.singleone.backend.upload.journey.JourneyEventStore;
import com.singleone.backend.upload.journey.JourneyRow;

import jakarta.persistence.EntityManager;

/**
 * PRD 9장 Journey & Attribution 종단 검증(ClickHouse 조회 포함). 이 PC의 Docker
 * Desktop/Testcontainers 비호환으로 자동 실행은 안 되며(기존 DetailServiceTest 등과 동일한
 * 환경 제약), bootRun+curl로 수동 검증한다.
 */
class JourneyAnalysisServiceTest extends AbstractIntegrationTest {

	@Autowired
	private JourneyAnalysisService journeyAnalysisService;

	@Autowired
	private AdvertiserRepository advertiserRepository;

	@Autowired
	private CampaignMasterRepository campaignMasterRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UploadBatchRepository uploadBatchRepository;

	@Autowired
	private JourneyEventStore journeyEventStore;

	@Autowired
	private EntityManager entityManager;

	private long successBatch(String advertiserId) {
		return uploadBatchRepository
			.saveAndFlush(new UploadBatch(advertiserId, UploadType.JOURNEY, "journey-test.csv", UploadStatus.SUCCESS))
			.getUploadBatchId();
	}

	private JourneyRow click(String advertiserId, String user, Media media, String campaignId, Instant time) {
		return new JourneyRow("click-" + user + "-" + media + "-" + time, advertiserId, user, time,
			JourneyEventType.CLICK, media, campaignId, null, null, null, null);
	}

	private JourneyRow purchase(String advertiserId, String user, Instant time, long revenue) {
		return new JourneyRow("purchase-" + user + "-" + time, advertiserId, user, time,
			JourneyEventType.PURCHASE, null, null, null, null, "order-" + user + "-" + time, new BigDecimal(revenue));
	}

	private Project setUpProject(String advertiserId) {
		advertiserRepository.save(new Advertiser(advertiserId, "Journey광고주"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.META, "c1"), "메타캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.GOOGLE, "c1"), "구글캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.TIKTOK, "c1"), "틱톡캠페인"));

		Project project = projectRepository.saveAndFlush(new Project(advertiserId, "Journey프로젝트", false, false));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.META, "c1")));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.GOOGLE, "c1")));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.TIKTOK, "c1")));
		entityManager.flush();
		return project;
	}

	@Test
	void goldenJourneyDatasetViaRealClickHouseQueryMatchesCalculatorResult() {
		String advertiserId = "adv-journey-e2e";
		Project project = setUpProject(advertiserId);
		long batchId = successBatch(advertiserId);

		journeyEventStore.insertBatch(List.of(
			click(advertiserId, "U001", Media.META, "c1", Instant.parse("2026-07-08T10:00:00Z")),
			click(advertiserId, "U001", Media.GOOGLE, "c1", Instant.parse("2026-07-09T10:00:00Z")),
			purchase(advertiserId, "U001", Instant.parse("2026-07-10T10:00:00Z"), 100_000),

			click(advertiserId, "U002", Media.TIKTOK, "c1", Instant.parse("2026-07-08T11:00:00Z")),
			click(advertiserId, "U002", Media.META, "c1", Instant.parse("2026-07-09T11:00:00Z")),
			purchase(advertiserId, "U002", Instant.parse("2026-07-10T11:00:00Z"), 120_000),

			click(advertiserId, "U003", Media.GOOGLE, "c1", Instant.parse("2026-07-09T12:00:00Z")),
			purchase(advertiserId, "U003", Instant.parse("2026-07-10T12:00:00Z"), 80_000),

			click(advertiserId, "U004", Media.META, "c1", Instant.parse("2026-07-08T09:00:00Z")),
			click(advertiserId, "U004", Media.GOOGLE, "c1", Instant.parse("2026-07-08T15:00:00Z")),
			click(advertiserId, "U004", Media.TIKTOK, "c1", Instant.parse("2026-07-09T09:00:00Z")),
			purchase(advertiserId, "U004", Instant.parse("2026-07-10T09:00:00Z"), 150_000)),
			batchId);

		JourneyAnalysisResult result = journeyAnalysisService.getJourneyAnalysis(project.getProjectId(),
			LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));

		assertThat(result.attributedJourneyCount()).isEqualTo(4);
		assertThat(result.channelPairs()).hasSize(3);
	}

	@Test
	void nonExistentProjectIsRejected() {
		assertThatThrownBy(() -> journeyAnalysisService.getJourneyAnalysis(-1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10)))
			.isInstanceOf(ProjectRequestException.class);
	}

}
