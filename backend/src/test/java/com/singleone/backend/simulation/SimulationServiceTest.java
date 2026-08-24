package com.singleone.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
import com.singleone.backend.project.ProjectRequestException;
import com.singleone.backend.upload.performance.PerformanceFactStore;
import com.singleone.backend.upload.performance.PerformanceRow;

import jakarta.persistence.EntityManager;

/** PRD 10장 Media Planning Simulation 종단 검증(ClickHouse 조회 포함). */
class SimulationServiceTest extends AbstractIntegrationTest {

	@Autowired
	private SimulationService simulationService;

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

	private static final LocalDate BASE_TO = LocalDate.of(2026, 7, 28);
	// 최근 8주(과거->최근): cost는 등차, 구매/매출은 체감형(로그형)으로 손으로 설계함.
	private static final long[] WEEKLY_COST = {700_000, 980_000, 1_260_000, 1_540_000, 1_820_000, 2_100_000, 2_380_000, 2_660_000};
	private static final long[] WEEKLY_RAW_PURCHASES = {62, 85, 100, 111, 120, 128, 134, 139};
	private static final long[] WEEKLY_RAW_REVENUE = {1_230_769, 1_692_308, 2_000_000, 2_215_385, 2_400_000, 2_553_846, 2_676_923, 2_769_231};

	private long successBatch(String advertiserId) {
		return uploadBatchRepository
			.saveAndFlush(new UploadBatch(advertiserId, UploadType.PERFORMANCE, "simulation-test.csv", UploadStatus.SUCCESS))
			.getUploadBatchId();
	}

	private PerformanceRow row(LocalDate date, String advertiserId, Media media, long cost, long purchases, long revenue) {
		return new PerformanceRow(date, advertiserId, "광고주", media, "c1", "캠페인",
			"ag-1", "광고그룹1", "ad-1", "광고1", 50_000, 1_000, new BigDecimal(cost), 0, purchases, new BigDecimal(revenue));
	}

	private Project setUpProject(String advertiserId) {
		advertiserRepository.save(new Advertiser(advertiserId, "Simulation광고주"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.META, "c1"), "메타캠페인"));
		campaignMasterRepository.save(new CampaignMaster(new CampaignMasterId(advertiserId, Media.GOOGLE, "c1"), "구글캠페인"));

		Project project = projectRepository.saveAndFlush(new Project(advertiserId, "Simulation프로젝트", false, false));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.META, "c1")));
		entityManager.persist(new ProjectCampaign(new ProjectCampaignId(project.getProjectId(), advertiserId, Media.GOOGLE, "c1")));
		entityManager.flush();
		return project;
	}

	@Test
	void mediaWithSufficientHistoryIsPredictableWhileMediaWithSparseHistoryIsNot() {
		String advertiserId = "adv-simulation-e2e";
		Project project = setUpProject(advertiserId);
		long batch = successBatch(advertiserId);

		List<PerformanceRow> rows = new java.util.ArrayList<>();
		for (int i = 0; i < 8; i++) {
			LocalDate weekEnd = BASE_TO.minusDays(7L * (7 - i));
			rows.add(row(weekEnd, advertiserId, Media.META, WEEKLY_COST[i], WEEKLY_RAW_PURCHASES[i], WEEKLY_RAW_REVENUE[i]));
		}
		// GOOGLE은 3주치만 있어 유효 주차 6 미만(PRD 10.5) -> 예측 불가여야 한다.
		for (int i = 5; i < 8; i++) {
			LocalDate weekEnd = BASE_TO.minusDays(7L * (7 - i));
			rows.add(row(weekEnd, advertiserId, Media.GOOGLE, WEEKLY_COST[i], WEEKLY_RAW_PURCHASES[i], WEEKLY_RAW_REVENUE[i]));
		}
		performanceFactStore.insertBatch(rows, batch);

		SimulationRequest request = new SimulationRequest(BASE_TO.minusDays(6), BASE_TO,
			LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14),
			Map.of(Media.META, new BigDecimal("2000000"), Media.GOOGLE, new BigDecimal("1000000")));

		SimulationResult result = simulationService.simulate(project.getProjectId(), request);

		MediaSimulationResult meta = result.mediaResults().stream().filter(r -> r.media() == Media.META).findFirst().orElseThrow();
		MediaSimulationResult google = result.mediaResults().stream().filter(r -> r.media() == Media.GOOGLE).findFirst().orElseThrow();

		assertThat(meta.confidence()).isNotEqualTo(ConfidenceLevel.UNAVAILABLE);
		assertThat(meta.predictedPurchases()).isNotNull();
		assertThat(meta.predictedPurchases().signum()).isPositive();
		assertThat(meta.curvePoints()).isNotEmpty();

		assertThat(google.confidence()).isEqualTo(ConfidenceLevel.UNAVAILABLE);
		assertThat(google.predictedPurchases()).isNull();
		assertThat(google.notes()).contains("데이터 부족");

		// GOOGLE 예산(1,000,000)이 0보다 크고 예측 불가이므로 전체 KPI는 산출 불가(AC-52).
		assertThat(result.totalAvailable()).isFalse();
		assertThat(result.totalPredictedPurchases()).isNull();
	}

	@Test
	void zeroBudgetPredictsZeroWithoutBlockingOtherMedia() {
		String advertiserId = "adv-simulation-zero";
		Project project = setUpProject(advertiserId);
		long batch = successBatch(advertiserId);

		List<PerformanceRow> rows = new java.util.ArrayList<>();
		for (int i = 0; i < 8; i++) {
			LocalDate weekEnd = BASE_TO.minusDays(7L * (7 - i));
			rows.add(row(weekEnd, advertiserId, Media.META, WEEKLY_COST[i], WEEKLY_RAW_PURCHASES[i], WEEKLY_RAW_REVENUE[i]));
		}
		performanceFactStore.insertBatch(rows, batch);

		SimulationRequest request = new SimulationRequest(BASE_TO.minusDays(6), BASE_TO,
			LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14),
			Map.of(Media.META, BigDecimal.ZERO, Media.GOOGLE, BigDecimal.ZERO));

		SimulationResult result = simulationService.simulate(project.getProjectId(), request);

		assertThat(result.totalAvailable()).isTrue();
		assertThat(result.totalPredictedPurchases()).isEqualByComparingTo(BigDecimal.ZERO);
		MediaSimulationResult meta = result.mediaResults().stream().filter(r -> r.media() == Media.META).findFirst().orElseThrow();
		assertThat(meta.confidence()).isNull();
		assertThat(meta.predictedPurchases()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void ac44And45And46_budgetAndConversionFormulasAreExact() {
		String advertiserId = "adv-simulation-formulas";
		Project project = setUpProject(advertiserId);
		long batch = successBatch(advertiserId);
		List<PerformanceRow> rows = new java.util.ArrayList<>();
		for (int i = 0; i < 8; i++) {
			LocalDate weekEnd = BASE_TO.minusDays(7L * (7 - i));
			rows.add(row(weekEnd, advertiserId, Media.META, WEEKLY_COST[i], WEEKLY_RAW_PURCHASES[i], WEEKLY_RAW_REVENUE[i]));
		}
		performanceFactStore.insertBatch(rows, batch);

		long baseDays = 7;
		long simDays = 14;
		BigDecimal inputBudget = new BigDecimal("2000000");
		SimulationRequest request = new SimulationRequest(BASE_TO.minusDays(baseDays - 1), BASE_TO,
			LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14), Map.of(Media.META, inputBudget, Media.GOOGLE, new BigDecimal("500000")));

		SimulationResult result = simulationService.simulate(project.getProjectId(), request);
		MediaSimulationResult meta = result.mediaResults().stream().filter(r -> r.media() == Media.META).findFirst().orElseThrow();

		// AC-44: 총예산은 매체별 입력 예산의 합산 그대로다(모델 유효성과 무관).
		assertThat(result.totalBudget()).isEqualByComparingTo("2500000");

		// AC-45: 환산 현재 운영 = 기준기간 Cost / 기준기간 일수 * 시뮬레이션 일수. 기준기간(baseDays=7일)이 정확히
		// 8주차 한 주와 겹쳐 그 주의 cost(2,660,000) 하나만 원본 합계로 들어간다.
		BigDecimal metaWeek8Cost = new BigDecimal(WEEKLY_COST[7]);
		BigDecimal expectedConvertedCurrent = metaWeek8Cost.divide(new BigDecimal(baseDays), java.math.MathContext.DECIMAL128)
			.multiply(new BigDecimal(simDays));
		assertThat(meta.convertedCurrentBudget()).isEqualByComparingTo(expectedConvertedCurrent);

		// AC-46: weekly_sim_budget = 입력예산 / 시뮬레이션일수 * 7.
		BigDecimal expectedWeeklyBudget = inputBudget.divide(new BigDecimal(simDays), java.math.MathContext.DECIMAL128)
			.multiply(new BigDecimal(7));
		assertThat(meta.weeklyBudget()).isEqualByComparingTo(expectedWeeklyBudget);
		assertThat(meta.weeklyBudget()).isEqualByComparingTo("1000000");
	}

	@Test
	void ac49_weeklyBudgetOver150PercentOfHistoricalMaxIsUnavailable() {
		String advertiserId = "adv-simulation-over150";
		Project project = setUpProject(advertiserId);
		long batch = successBatch(advertiserId);
		List<PerformanceRow> rows = new java.util.ArrayList<>();
		for (int i = 0; i < 8; i++) {
			LocalDate weekEnd = BASE_TO.minusDays(7L * (7 - i));
			rows.add(row(weekEnd, advertiserId, Media.META, WEEKLY_COST[i], WEEKLY_RAW_PURCHASES[i], WEEKLY_RAW_REVENUE[i]));
		}
		performanceFactStore.insertBatch(rows, batch);

		// historicalMax=2,660,000 -> 150% = 3,990,000(주간). simDays=14이므로 예산 9,000,000 -> weekly=4,500,000(초과).
		SimulationRequest request = new SimulationRequest(BASE_TO.minusDays(6), BASE_TO,
			LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14), Map.of(Media.META, new BigDecimal("9000000")));

		SimulationResult result = simulationService.simulate(project.getProjectId(), request);
		MediaSimulationResult meta = result.mediaResults().stream().filter(r -> r.media() == Media.META).findFirst().orElseThrow();

		assertThat(meta.confidence()).isEqualTo(ConfidenceLevel.UNAVAILABLE);
		assertThat(meta.predictedPurchases()).isNull();
		assertThat(meta.notes()).contains("과거 운영 범위 초과", "포화구간 진입 가능성");
	}

	@Test
	void ac50_extrapolationZonesBelowMinAndAboveMaxYieldLowConfidence() {
		String advertiserId = "adv-simulation-lowconf";
		Project project = setUpProject(advertiserId);
		long batch = successBatch(advertiserId);
		List<PerformanceRow> rows = new java.util.ArrayList<>();
		for (int i = 0; i < 8; i++) {
			LocalDate weekEnd = BASE_TO.minusDays(7L * (7 - i));
			rows.add(row(weekEnd, advertiserId, Media.META, WEEKLY_COST[i], WEEKLY_RAW_PURCHASES[i], WEEKLY_RAW_REVENUE[i]));
		}
		performanceFactStore.insertBatch(rows, batch);

		// historicalMin=700,000, historicalMax=2,660,000. simDays=14.
		// 최대 초과~150% 이하: weekly=3,000,000 -> budget=6,000,000.
		SimulationRequest overMax = new SimulationRequest(BASE_TO.minusDays(6), BASE_TO,
			LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14), Map.of(Media.META, new BigDecimal("6000000")));
		MediaSimulationResult overMaxResult = simulationService.simulate(project.getProjectId(), overMax).mediaResults().stream()
			.filter(r -> r.media() == Media.META).findFirst().orElseThrow();
		assertThat(overMaxResult.confidence()).isEqualTo(ConfidenceLevel.LOW);
		assertThat(overMaxResult.predictedPurchases()).isNotNull();

		// 0보다 크고 최소 미만: weekly=500,000 -> budget=1,000,000.
		SimulationRequest underMin = new SimulationRequest(BASE_TO.minusDays(6), BASE_TO,
			LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14), Map.of(Media.META, new BigDecimal("1000000")));
		MediaSimulationResult underMinResult = simulationService.simulate(project.getProjectId(), underMin).mediaResults().stream()
			.filter(r -> r.media() == Media.META).findFirst().orElseThrow();
		assertThat(underMinResult.confidence()).isEqualTo(ConfidenceLevel.LOW);
		assertThat(underMinResult.predictedPurchases()).isNotNull();
	}

	@Test
	void systemDefaultProjectIsRejected() {
		String advertiserId = "adv-simulation-system";
		advertiserRepository.save(new Advertiser(advertiserId, "시스템광고주"));
		Project systemProject = projectRepository.saveAndFlush(new Project(advertiserId, "전체 캠페인", true, true));

		SimulationRequest request = new SimulationRequest(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7),
			LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7), Map.of());

		assertThatThrownBy(() -> simulationService.simulate(systemProject.getProjectId(), request))
			.isInstanceOf(ProjectRequestException.class);
	}

}
