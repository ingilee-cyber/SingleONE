package com.singleone.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.singleone.backend.AbstractIntegrationTest;
import com.singleone.backend.analytics.MediaIndexResult;
import com.singleone.backend.domain.advertiser.Advertiser;
import com.singleone.backend.domain.advertiser.AdvertiserRepository;
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
import com.singleone.backend.upload.performance.PerformanceFactStore;
import com.singleone.backend.upload.performance.PerformanceRow;

import jakarta.persistence.EntityManager;

/** PRD 6장 Dashboard 응답 조립(정렬/합계/이전기간/rolling 포함 여부) 검증. */
class DashboardServiceTest extends AbstractIntegrationTest {

	@Autowired
	private DashboardService dashboardService;

	@Autowired
	private AdvertiserRepository advertiserRepository;

	@Autowired
	private CampaignMasterRepository campaignMasterRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UploadBatchRepository uploadBatchRepository;

	@Autowired
	private PerformanceFactStore performanceFactStore;

	@Autowired
	private EntityManager entityManager;

	private PerformanceRow row(LocalDate date, String advertiserId, Media media, String campaignId, long cost, long purchases) {
		return new PerformanceRow(date, advertiserId, "광고주", media, campaignId, "캠페인",
			"ag-1", "광고그룹", "ad-1", "광고", 50_000, 1_000, new BigDecimal(cost), 0, purchases, new BigDecimal(cost));
	}

	@Test
	void dashboardIncludesSortedCurrentPreviousTotalsAndRolling() {
		String advertiserId = "adv-dash-1";
		advertiserRepository.save(new Advertiser(advertiserId, "대시보드광고주1"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.META, "camp-meta"), "메타캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.GOOGLE, "camp-google"), "구글캠페인"));

		Project project = projectRepository.saveAndFlush(new Project(advertiserId, "대시보드프로젝트1", false, false));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.META, "camp-meta")));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.GOOGLE, "camp-google")));
		entityManager.flush();

		long batch = uploadBatchRepository
			.saveAndFlush(new UploadBatch(advertiserId, UploadType.PERFORMANCE, "dash.csv", UploadStatus.SUCCESS))
			.getUploadBatchId();

		LocalDate currentFrom = LocalDate.of(2026, 6, 8);
		LocalDate currentTo = LocalDate.of(2026, 6, 14);
		LocalDate previousFrom = LocalDate.of(2026, 6, 1);

		List<PerformanceRow> rows = new ArrayList<>();
		for (LocalDate date = previousFrom; !date.isAfter(currentTo); date = date.plusDays(1)) {
			// GOOGLE이 META보다 더 높은 효율을 갖도록 해 Index 정렬(높은 순)을 검증한다.
			rows.add(row(date, advertiserId, Media.META, "camp-meta", 500_000, 5));
			rows.add(row(date, advertiserId, Media.GOOGLE, "camp-google", 500_000, 20));
		}
		performanceFactStore.insertBatch(rows, batch);

		DashboardResponse response = dashboardService.getDashboard(project.getProjectId(), currentFrom, currentTo);

		assertThat(response.current()).hasSize(2);
		assertThat(response.current().get(0).media()).isEqualTo(Media.GOOGLE);
		assertThat(response.current()).allMatch(r -> r.status().name().equals("VALID"));
		assertThat(response.currentTotals().cost()).isEqualByComparingTo("7000000");

		assertThat(response.previous()).hasSize(2);
		assertThat(response.previousTotals()).isNotNull();

		assertThat(response.rolling()).isNotEmpty();
		assertThat(response.rolling()).allSatisfy(point ->
			assertThat(point.mediaResults()).extracting(MediaIndexResult::media).contains(Media.META, Media.GOOGLE));
	}

}
