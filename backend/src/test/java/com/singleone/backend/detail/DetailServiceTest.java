package com.singleone.backend.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.singleone.backend.AbstractIntegrationTest;
import com.singleone.backend.analytics.MediaIndexResult;
import com.singleone.backend.analytics.SingleOnePerformanceService;
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
import com.singleone.backend.project.ProjectRequestException;
import com.singleone.backend.upload.performance.PerformanceFactStore;
import com.singleone.backend.upload.performance.PerformanceRow;

import jakarta.persistence.EntityManager;

/** PRD 7장 매체/캠페인/광고그룹/광고 상세 검증. */
class DetailServiceTest extends AbstractIntegrationTest {

	@Autowired
	private DetailService detailService;

	@Autowired
	private SingleOnePerformanceService performanceService;

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

	private long successBatch(String advertiserId) {
		return uploadBatchRepository
			.saveAndFlush(new UploadBatch(advertiserId, UploadType.PERFORMANCE, "detail-test.csv", UploadStatus.SUCCESS))
			.getUploadBatchId();
	}

	private PerformanceRow row(LocalDate date, String advertiserId, Media media, String campaignId, String adGroupId,
			String adId, long cost, long purchases) {
		return new PerformanceRow(date, advertiserId, "광고주", media, campaignId, "캠페인",
			adGroupId, "광고그룹" + adGroupId, adId, "광고" + adId, 50_000, 1_000, new BigDecimal(cost), 0, purchases, new BigDecimal(cost));
	}

	private Project setUpTwoMediaProject(String advertiserId) {
		advertiserRepository.save(new Advertiser(advertiserId, "상세광고주"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.META, "camp-meta"), "메타캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.GOOGLE, "camp-google"), "구글캠페인"));

		Project project = projectRepository.saveAndFlush(new Project(advertiserId, "상세프로젝트", false, false));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.META, "camp-meta")));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.GOOGLE, "camp-google")));
		entityManager.flush();
		return project;
	}

	@Test
	void mediaDetailMatchesDashboardIndexForThatMedia() {
		String advertiserId = "adv-detail-1";
		Project project = setUpTwoMediaProject(advertiserId);

		long batch = successBatch(advertiserId);
		LocalDate from = LocalDate.of(2026, 7, 1);
		LocalDate to = LocalDate.of(2026, 7, 10);
		List<PerformanceRow> rows = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			rows.add(row(date, advertiserId, Media.META, "camp-meta", "ag-1", "ad-1", 500_000, 5));
			rows.add(row(date, advertiserId, Media.GOOGLE, "camp-google", "ag-1", "ad-1", 500_000, 20));
		}
		performanceFactStore.insertBatch(rows, batch);

		MediaDetailResponse mediaDetail = detailService.getMediaDetail(project.getProjectId(), Media.GOOGLE, from, to);
		List<MediaIndexResult> dashboardCurrent = performanceService.calculatePeriod(project.getProjectId(), from, to);
		MediaIndexResult expected = dashboardCurrent.stream().filter(r -> r.media() == Media.GOOGLE).findFirst().orElseThrow();

		assertThat(mediaDetail.current().indexScore()).isEqualByComparingTo(expected.indexScore());
		assertThat(mediaDetail.current().status()).isEqualTo(expected.status());
		assertThat(mediaDetail.rolling()).isNotEmpty();
	}

	@Test
	void campaignDetailComparesCurrentAndPreviousPeriodWithoutIndex() {
		String advertiserId = "adv-detail-2";
		Project project = setUpTwoMediaProject(advertiserId);

		long batch = successBatch(advertiserId);
		LocalDate previousFrom = LocalDate.of(2026, 7, 1);
		LocalDate currentFrom = LocalDate.of(2026, 7, 8);
		LocalDate currentTo = LocalDate.of(2026, 7, 14);
		List<PerformanceRow> rows = new ArrayList<>();
		for (LocalDate date = previousFrom; !date.isAfter(currentTo); date = date.plusDays(1)) {
			long purchases = date.isBefore(currentFrom) ? 2 : 10;
			rows.add(row(date, advertiserId, Media.META, "camp-meta", "ag-1", "ad-1", 100_000, purchases));
			rows.add(row(date, advertiserId, Media.GOOGLE, "camp-google", "ag-1", "ad-1", 100_000, 5));
		}
		performanceFactStore.insertBatch(rows, batch);

		EntityPerformanceComparison comparison = detailService.getCampaignDetail(project.getProjectId(), Media.META,
			"camp-meta", currentFrom, currentTo);

		assertThat(comparison.current().singleOnePerformance().singleOnePurchases())
			.isGreaterThan(comparison.previous().singleOnePerformance().singleOnePurchases());
		assertThat(comparison.current().name()).isEqualTo("메타캠페인");
	}

	@Test
	void adGroupAndAdDetailProvidePerformanceWithoutIndexOrPrevious() {
		String advertiserId = "adv-detail-3";
		Project project = setUpTwoMediaProject(advertiserId);

		long batch = successBatch(advertiserId);
		LocalDate from = LocalDate.of(2026, 7, 1);
		LocalDate to = LocalDate.of(2026, 7, 3);
		List<PerformanceRow> rows = List.of(
			row(from, advertiserId, Media.META, "camp-meta", "ag-1", "ad-1", 100_000, 3),
			row(from.plusDays(1), advertiserId, Media.META, "camp-meta", "ag-1", "ad-2", 50_000, 1));
		performanceFactStore.insertBatch(rows, batch);

		EntityPerformance adGroup = detailService.getAdGroupDetail(project.getProjectId(), Media.META, "camp-meta", "ag-1", from, to);
		assertThat(adGroup.rawTotals().cost()).isEqualByComparingTo("150000");
		assertThat(adGroup.name()).isEqualTo("광고그룹ag-1");

		EntityPerformance ad = detailService.getAdDetail(project.getProjectId(), Media.META, "camp-meta", "ag-1", "ad-1", from, to);
		assertThat(ad.rawTotals().cost()).isEqualByComparingTo("100000");
		assertThat(ad.name()).isEqualTo("광고ad-1");
	}

	@Test
	void listCampaignsSupportsSearchSortAndPageSizeClamp() {
		String advertiserId = "adv-detail-4";
		advertiserRepository.save(new Advertiser(advertiserId, "상세광고주4"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.META, "camp-a"), "여름캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.META, "camp-b"), "겨울캠페인"));

		Project project = projectRepository.saveAndFlush(new Project(advertiserId, "리스트프로젝트", false, false));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.META, "camp-a")));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.META, "camp-b")));
		entityManager.flush();

		long batch = successBatch(advertiserId);
		LocalDate date = LocalDate.of(2026, 7, 1);
		performanceFactStore.insertBatch(List.of(
			row(date, advertiserId, Media.META, "camp-a", "ag-1", "ad-1", 300_000, 10)), batch);

		var page = detailService.listCampaigns(project.getProjectId(), Media.META, date, date, "여름",
			PageRequest.of(0, 300, Sort.by("cost").descending()));
		assertThat(page.getSize()).isEqualTo(200); // 300 요청해도 200으로 clamp
		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().get(0).name()).isEqualTo("여름캠페인");
	}

	@Test
	void requestingMediaNotInProjectIsRejected() {
		String advertiserId = "adv-detail-5";
		Project project = setUpTwoMediaProject(advertiserId);
		LocalDate date = LocalDate.of(2026, 7, 1);

		assertThatThrownBy(() -> detailService.getMediaDetail(project.getProjectId(), Media.NAVER, date, date))
			.isInstanceOf(ProjectRequestException.class);
	}

	@Test
	void requestingCampaignNotInProjectIsRejected() {
		String advertiserId = "adv-detail-6";
		Project project = setUpTwoMediaProject(advertiserId);
		LocalDate date = LocalDate.of(2026, 7, 1);

		assertThatThrownBy(() -> detailService.getCampaignDetail(project.getProjectId(), Media.META, "no-such-campaign", date, date))
			.isInstanceOf(ProjectRequestException.class);
	}

}
