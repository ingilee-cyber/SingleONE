package com.singleone.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.singleone.backend.AbstractIntegrationTest;
import com.singleone.backend.domain.advertiser.Advertiser;
import com.singleone.backend.domain.advertiser.AdvertiserRepository;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.master.CampaignMaster;
import com.singleone.backend.domain.master.CampaignMasterId;
import com.singleone.backend.domain.master.CampaignMasterRepository;
import com.singleone.backend.domain.project.ProjectCampaignId;
import com.singleone.backend.domain.upload.UploadBatch;
import com.singleone.backend.domain.upload.UploadBatchRepository;
import com.singleone.backend.domain.upload.UploadStatus;
import com.singleone.backend.domain.upload.UploadType;
import com.singleone.backend.upload.performance.PerformanceFactStore;
import com.singleone.backend.upload.performance.PerformanceRow;

/**
 * PRD 11.6(최신 SUCCESS batch가 natural key의 유효값)과 프로젝트/광고주 범위 제한을
 * ClickHouse 집계 쿼리가 정확히 반영하는지 검증한다.
 */
class PerformanceAggregationRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private PerformanceAggregationRepository aggregationRepository;

	@Autowired
	private PerformanceFactStore performanceFactStore;

	@Autowired
	private AdvertiserRepository advertiserRepository;

	@Autowired
	private CampaignMasterRepository campaignMasterRepository;

	@Autowired
	private UploadBatchRepository uploadBatchRepository;

	private long successBatch(String advertiserId, String filename) {
		return uploadBatchRepository
			.saveAndFlush(new UploadBatch(advertiserId, UploadType.PERFORMANCE, filename, UploadStatus.SUCCESS))
			.getUploadBatchId();
	}

	private PerformanceRow row(LocalDate date, String advertiserId, Media media, String campaignId, long cost, long purchases) {
		return new PerformanceRow(date, advertiserId, "광고주", media, campaignId, "캠페인",
			"ag-1", "광고그룹", "ad-1", "광고", 1000, 100, new BigDecimal(cost), 0, purchases, new BigDecimal(cost));
	}

	@Test
	void latestSuccessBatchWinsOnDuplicateNaturalKey() {
		advertiserRepository.save(new Advertiser("adv-agg-1", "집계광고주1"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId("adv-agg-1", Media.META, "camp-1"), "캠페인1"));

		LocalDate date = LocalDate.of(2026, 1, 1);
		long firstBatch = successBatch("adv-agg-1", "first.csv");
		performanceFactStore.insertBatch(List.of(row(date, "adv-agg-1", Media.META, "camp-1", 1_000, 10)), firstBatch);
		long secondBatch = successBatch("adv-agg-1", "second.csv");
		performanceFactStore.insertBatch(List.of(row(date, "adv-agg-1", Media.META, "camp-1", 2_000, 20)), secondBatch);

		List<ProjectCampaignId> campaigns = List.of(new ProjectCampaignId(1L, "adv-agg-1", Media.META, "camp-1"));
		List<DailyMediaTotal> results = aggregationRepository.fetchDailyMediaTotals("adv-agg-1", campaigns, date, date);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).cost()).isEqualByComparingTo("2000");
		assertThat(results.get(0).rawPurchases()).isEqualByComparingTo("20");
	}

	@Test
	void zeroCostDayIsReturnedSeparatelyFromNonZeroDays() {
		advertiserRepository.save(new Advertiser("adv-agg-2", "집계광고주2"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId("adv-agg-2", Media.GOOGLE, "camp-2"), "캠페인2"));

		long batch = successBatch("adv-agg-2", "days.csv");
		LocalDate day1 = LocalDate.of(2026, 2, 1);
		LocalDate day2 = LocalDate.of(2026, 2, 2);
		performanceFactStore.insertBatch(List.of(
			row(day1, "adv-agg-2", Media.GOOGLE, "camp-2", 0, 0),
			row(day2, "adv-agg-2", Media.GOOGLE, "camp-2", 5_000, 5)), batch);

		List<ProjectCampaignId> campaigns = List.of(new ProjectCampaignId(1L, "adv-agg-2", Media.GOOGLE, "camp-2"));
		List<DailyMediaTotal> results = aggregationRepository.fetchDailyMediaTotals("adv-agg-2", campaigns, day1, day2);

		assertThat(results).hasSize(2);
		assertThat(results.stream().filter(r -> r.date().equals(day1)).findFirst().orElseThrow().cost())
			.isEqualByComparingTo("0");
		assertThat(results.stream().filter(r -> r.date().equals(day2)).findFirst().orElseThrow().cost())
			.isEqualByComparingTo("5000");
	}

	@Test
	void excludesCampaignsNotInProjectAndOtherAdvertisers() {
		advertiserRepository.save(new Advertiser("adv-agg-3", "집계광고주3"));
		advertiserRepository.save(new Advertiser("adv-agg-3-other", "다른광고주"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId("adv-agg-3", Media.NAVER, "camp-in"), "포함캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId("adv-agg-3", Media.NAVER, "camp-out"), "제외캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId("adv-agg-3-other", Media.NAVER, "camp-in"), "다른광고주캠페인"));

		long batch = successBatch("adv-agg-3", "in.csv");
		long otherAdvertiserBatch = successBatch("adv-agg-3-other", "other.csv");
		LocalDate date = LocalDate.of(2026, 3, 1);
		performanceFactStore.insertBatch(List.of(row(date, "adv-agg-3", Media.NAVER, "camp-in", 1_000, 1)), batch);
		performanceFactStore.insertBatch(List.of(row(date, "adv-agg-3", Media.NAVER, "camp-out", 9_999, 9)), batch);
		performanceFactStore.insertBatch(List.of(row(date, "adv-agg-3-other", Media.NAVER, "camp-in", 8_888, 8)), otherAdvertiserBatch);

		List<ProjectCampaignId> campaigns = List.of(new ProjectCampaignId(1L, "adv-agg-3", Media.NAVER, "camp-in"));
		List<DailyMediaTotal> results = aggregationRepository.fetchDailyMediaTotals("adv-agg-3", campaigns, date, date);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).cost()).isEqualByComparingTo("1000");
	}

}
