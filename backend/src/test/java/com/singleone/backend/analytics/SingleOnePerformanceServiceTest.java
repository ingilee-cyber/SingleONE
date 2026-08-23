package com.singleone.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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

/**
 * PRD 8.8(이전 기간 비교, AC-14)/8.9(7일 Rolling Index, AC-15/AC-16) 검증.
 */
class SingleOnePerformanceServiceTest extends AbstractIntegrationTest {

	@Autowired
	private SingleOnePerformanceService service;

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
			.saveAndFlush(new UploadBatch(advertiserId, UploadType.PERFORMANCE, "svc-test.csv", UploadStatus.SUCCESS))
			.getUploadBatchId();
	}

	private PerformanceRow row(LocalDate date, String advertiserId, Media media, String campaignId, long cost, long purchases) {
		return new PerformanceRow(date, advertiserId, "광고주", media, campaignId, "캠페인",
			"ag-1", "광고그룹", "ad-1", "광고", 50_000, 1_000, new BigDecimal(cost), 0, purchases, new BigDecimal(cost));
	}

	@Test
	void previousPeriodInsufficientDataReflectedInComparison() {
		String advertiserId = "adv-svc-1";
		advertiserRepository.save(new Advertiser(advertiserId, "서비스광고주1"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.META, "camp-meta"), "메타캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.GOOGLE, "camp-google"), "구글캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.NAVER, "camp-naver"), "네이버캠페인"));

		Project project = projectRepository.saveAndFlush(new Project(advertiserId, "이전기간비교프로젝트", false, false));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.META, "camp-meta")));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.GOOGLE, "camp-google")));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.NAVER, "camp-naver")));
		entityManager.flush();

		LocalDate currentFrom = LocalDate.of(2026, 4, 8);
		LocalDate currentTo = LocalDate.of(2026, 4, 14); // 7일
		LocalDate previousFrom = LocalDate.of(2026, 4, 1); // 동일 길이 직전 기간(PRD 8.8)

		long batch = successBatch(advertiserId);
		List<PerformanceRow> rows = new ArrayList<>();
		for (LocalDate date = previousFrom; !date.isAfter(currentTo); date = date.plusDays(1)) {
			rows.add(row(date, advertiserId, Media.META, "camp-meta", 500_000, 5));
			rows.add(row(date, advertiserId, Media.NAVER, "camp-naver", 500_000, 5));
			// GOOGLE은 이전 기간에만 극히 낮은 cost로 최소 조건 미달을 만든다.
			long googleCost = date.isBefore(currentFrom) ? 10_000 : 500_000;
			rows.add(row(date, advertiserId, Media.GOOGLE, "camp-google", googleCost, 5));
		}
		performanceFactStore.insertBatch(rows, batch);

		PeriodComparison comparison = service.calculatePeriodWithPreviousComparison(project.getProjectId(), advertiserId,
			currentFrom, currentTo);

		assertThat(status(comparison.current(), Media.META)).isEqualTo(IndexStatus.VALID);
		assertThat(status(comparison.current(), Media.GOOGLE)).isEqualTo(IndexStatus.VALID);
		assertThat(status(comparison.current(), Media.NAVER)).isEqualTo(IndexStatus.VALID);

		assertThat(status(comparison.previous(), Media.META)).isEqualTo(IndexStatus.VALID);
		assertThat(status(comparison.previous(), Media.NAVER)).isEqualTo(IndexStatus.VALID);
		assertThat(status(comparison.previous(), Media.GOOGLE)).isEqualTo(IndexStatus.INSUFFICIENT_DATA);
	}

	@Test
	void rollingIndexUsesDataBeforeSelectedRangeAndSkipsWindowsWithInsufficientMedia() {
		String advertiserId = "adv-svc-2";
		advertiserRepository.save(new Advertiser(advertiserId, "서비스광고주2"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.META, "camp-meta"), "메타캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.GOOGLE, "camp-google"), "구글캠페인"));

		Project project = projectRepository.saveAndFlush(new Project(advertiserId, "롤링프로젝트", false, false));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.META, "camp-meta")));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.GOOGLE, "camp-google")));
		entityManager.flush();

		long batch = successBatch(advertiserId);
		LocalDate dataFrom = LocalDate.of(2026, 5, 2);
		LocalDate dataTo = LocalDate.of(2026, 5, 17);
		LocalDate googleMissingDate = LocalDate.of(2026, 5, 3);

		List<PerformanceRow> rows = new ArrayList<>();
		for (LocalDate date = dataFrom; !date.isAfter(dataTo); date = date.plusDays(1)) {
			rows.add(row(date, advertiserId, Media.META, "camp-meta", 200_000, 3));
			if (!date.equals(googleMissingDate)) {
				rows.add(row(date, advertiserId, Media.GOOGLE, "camp-google", 200_000, 3));
			}
		}
		performanceFactStore.insertBatch(rows, batch);

		LocalDate rangeFrom = LocalDate.of(2026, 5, 8);
		LocalDate rangeTo = LocalDate.of(2026, 5, 14);
		List<RollingIndexPoint> points = service.calculateRollingIndex(project.getProjectId(), advertiserId, rangeFrom, rangeTo);

		// window가 5/3(GOOGLE 데이터 없음)을 포함하는 5/8, 5/9는 유효 매체가 1개뿐이라 결과에서 제외된다(AC-16).
		assertThat(points).extracting(RollingIndexPoint::date)
			.doesNotContain(LocalDate.of(2026, 5, 8), LocalDate.of(2026, 5, 9));
		// 5/10의 window[5/4,5/10]은 선택 기간(5/8~) 이전인 5/4~5/7 데이터까지 사용해 정상 산출된다(AC-15).
		assertThat(points).extracting(RollingIndexPoint::date)
			.contains(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 14));
		assertThat(points).allSatisfy(p -> assertThat(p.mediaResults())
			.filteredOn(r -> r.status() == IndexStatus.VALID).hasSize(2));
	}

	private IndexStatus status(List<MediaIndexResult> results, Media media) {
		return results.stream().filter(r -> r.media() == media).findFirst().orElseThrow().status();
	}

}
