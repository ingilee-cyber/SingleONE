package com.singleone.backend.detail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.singleone.backend.analytics.EntityAggregateRow;
import com.singleone.backend.analytics.MediaIndexResult;
import com.singleone.backend.analytics.MediaPerformanceTotals;
import com.singleone.backend.analytics.PerformanceAggregationRepository;
import com.singleone.backend.analytics.PeriodComparison;
import com.singleone.backend.analytics.RollingIndexPoint;
import com.singleone.backend.analytics.SingleOneIndexCalculator;
import com.singleone.backend.analytics.SingleOnePerformanceService;
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
import com.singleone.backend.domain.project.ProjectCampaignId;
import com.singleone.backend.domain.project.ProjectRepository;
import com.singleone.backend.project.ProjectRequestException;
import com.singleone.backend.project.ProjectService;

/**
 * PRD 7장 매체/캠페인/광고그룹/광고 상세. 계산은 전부 {@link SingleOneIndexCalculator}
 * (Stage 3)와 {@link SingleOnePerformanceService}(Stage 3/5, 매체 Index)를 재사용하고,
 * 여기서는 조회 범위 검증과 하위 목록 조립(검색/정렬/페이지네이션)만 한다.
 */
@Service
public class DetailService {

	private static final int DEFAULT_PAGE_SIZE = 50;
	private static final int MAX_PAGE_SIZE = 200;

	private final ProjectRepository projectRepository;
	private final ProjectService projectService;
	private final PerformanceAggregationRepository aggregationRepository;
	private final InternalMediaFilterRepository filterRepository;
	private final SingleOneIndexCalculator calculator;
	private final SingleOnePerformanceService performanceService;
	private final CampaignMasterRepository campaignMasterRepository;
	private final AdGroupMasterRepository adGroupMasterRepository;
	private final AdMasterRepository adMasterRepository;

	public DetailService(ProjectRepository projectRepository, ProjectService projectService,
			PerformanceAggregationRepository aggregationRepository, InternalMediaFilterRepository filterRepository,
			SingleOneIndexCalculator calculator, SingleOnePerformanceService performanceService,
			CampaignMasterRepository campaignMasterRepository, AdGroupMasterRepository adGroupMasterRepository,
			AdMasterRepository adMasterRepository) {
		this.projectRepository = projectRepository;
		this.projectService = projectService;
		this.aggregationRepository = aggregationRepository;
		this.filterRepository = filterRepository;
		this.calculator = calculator;
		this.performanceService = performanceService;
		this.campaignMasterRepository = campaignMasterRepository;
		this.adGroupMasterRepository = adGroupMasterRepository;
		this.adMasterRepository = adMasterRepository;
	}

	/** PRD 7.2: Dashboard(Stage 5)와 동일한 계산 결과에서 이 매체 1개만 추린다. */
	public MediaDetailResponse getMediaDetail(Long projectId, Media media, LocalDate from, LocalDate to) {
		Project project = getProjectOrThrow(projectId);
		requireMediaInProject(project, media);

		PeriodComparison comparison = performanceService.calculatePeriodWithPreviousComparison(projectId, from, to);
		List<RollingIndexPoint> rolling = performanceService.calculateRollingIndex(projectId, from, to);
		return new MediaDetailResponse(findMedia(comparison.current(), media), findMedia(comparison.previous(), media), rolling);
	}

	/** PRD 7.2: 매체 상세의 캠페인 목록(검색/정렬/페이지네이션). */
	public Page<EntityPerformance> listCampaigns(Long projectId, Media media, LocalDate from, LocalDate to,
			String search, Pageable pageable) {
		Project project = getProjectOrThrow(projectId);
		requireMediaInProject(project, media);

		List<EntityAggregateRow> rows = aggregationRepository.fetchEntityTotals(project.getAdvertiserId(), media,
			null, null, null, from, to);
		Map<String, MediaPerformanceTotals> totalsByCampaign = groupBy(rows, EntityAggregateRow::campaignId, media);
		Map<String, String> names = campaignMasterRepository.findByIdAdvertiserId(project.getAdvertiserId()).stream()
			.filter(cm -> cm.getId().getMedia() == media)
			.collect(Collectors.toMap(cm -> cm.getId().getCampaignId(), CampaignMaster::getLatestName));

		BigDecimal rate = filterRate(media);
		List<EntityPerformance> all = totalsByCampaign.entrySet().stream()
			.map(e -> toEntityPerformance(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()), e.getValue(), rate))
			.toList();
		return paginate(all, search, pageable);
	}

	/** PRD 7.3: 캠페인 상세(Index 없음, 이전 기간 있음). */
	public EntityPerformanceComparison getCampaignDetail(Long projectId, Media media, String campaignId,
			LocalDate from, LocalDate to) {
		Project project = getProjectOrThrow(projectId);
		requireCampaignInProject(project, media, campaignId);

		EntityPerformance current = campaignTotal(project, media, campaignId, from, to);
		LocalDate[] previousRange = previousRange(from, to);
		EntityPerformance previous = campaignTotal(project, media, campaignId, previousRange[0], previousRange[1]);
		return new EntityPerformanceComparison(current, previous);
	}

	/** PRD 7.3: 캠페인 상세의 광고그룹 목록. */
	public Page<EntityPerformance> listAdGroups(Long projectId, Media media, String campaignId, LocalDate from,
			LocalDate to, String search, Pageable pageable) {
		Project project = getProjectOrThrow(projectId);
		requireCampaignInProject(project, media, campaignId);

		List<EntityAggregateRow> rows = aggregationRepository.fetchEntityTotals(project.getAdvertiserId(), media,
			campaignId, null, null, from, to);
		Map<String, MediaPerformanceTotals> totalsByAdGroup = groupBy(rows, EntityAggregateRow::adGroupId, media);
		Map<String, String> names = adGroupMasterRepository
			.findByIdAdvertiserIdAndIdMediaAndIdCampaignId(project.getAdvertiserId(), media, campaignId).stream()
			.collect(Collectors.toMap(ag -> ag.getId().getAdGroupId(), AdGroupMaster::getLatestName));

		BigDecimal rate = filterRate(media);
		List<EntityPerformance> all = totalsByAdGroup.entrySet().stream()
			.map(e -> toEntityPerformance(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()), e.getValue(), rate))
			.toList();
		return paginate(all, search, pageable);
	}

	/** PRD 7.4: 광고그룹 상세(Index/이전 기간 없음). */
	public EntityPerformance getAdGroupDetail(Long projectId, Media media, String campaignId, String adGroupId,
			LocalDate from, LocalDate to) {
		Project project = getProjectOrThrow(projectId);
		requireAdGroupExists(project, media, campaignId, adGroupId);

		List<EntityAggregateRow> rows = aggregationRepository.fetchEntityTotals(project.getAdvertiserId(), media,
			campaignId, adGroupId, null, from, to);
		MediaPerformanceTotals totals = sumToTotals(media, rows);
		String name = adGroupMasterRepository.findById(new AdGroupMasterId(project.getAdvertiserId(), media, campaignId, adGroupId))
			.map(AdGroupMaster::getLatestName).orElse(adGroupId);
		return toEntityPerformance(adGroupId, name, totals, filterRate(media));
	}

	/** PRD 7.4: 광고그룹 상세의 광고 목록. */
	public Page<EntityPerformance> listAds(Long projectId, Media media, String campaignId, String adGroupId,
			LocalDate from, LocalDate to, String search, Pageable pageable) {
		Project project = getProjectOrThrow(projectId);
		requireAdGroupExists(project, media, campaignId, adGroupId);

		List<EntityAggregateRow> rows = aggregationRepository.fetchEntityTotals(project.getAdvertiserId(), media,
			campaignId, adGroupId, null, from, to);
		Map<String, MediaPerformanceTotals> totalsByAd = groupBy(rows, EntityAggregateRow::adId, media);
		Map<String, String> names = adMasterRepository
			.findByIdAdvertiserIdAndIdMediaAndIdCampaignIdAndIdAdGroupId(project.getAdvertiserId(), media, campaignId, adGroupId)
			.stream().collect(Collectors.toMap(ad -> ad.getId().getAdId(), AdMaster::getLatestName));

		BigDecimal rate = filterRate(media);
		List<EntityPerformance> all = totalsByAd.entrySet().stream()
			.map(e -> toEntityPerformance(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()), e.getValue(), rate))
			.toList();
		return paginate(all, search, pageable);
	}

	/** PRD 7.5: 광고 상세(Index/이전 기간/하위 목록 없음). */
	public EntityPerformance getAdDetail(Long projectId, Media media, String campaignId, String adGroupId, String adId,
			LocalDate from, LocalDate to) {
		Project project = getProjectOrThrow(projectId);
		AdMasterId id = new AdMasterId(project.getAdvertiserId(), media, campaignId, adGroupId, adId);
		AdMaster adMaster = adMasterRepository.findById(id)
			.orElseThrow(() -> new ProjectRequestException("존재하지 않는 광고입니다: " + adId));

		List<EntityAggregateRow> rows = aggregationRepository.fetchEntityTotals(project.getAdvertiserId(), media,
			campaignId, adGroupId, adId, from, to);
		MediaPerformanceTotals totals = sumToTotals(media, rows);
		return toEntityPerformance(adId, adMaster.getLatestName(), totals, filterRate(media));
	}

	private EntityPerformance campaignTotal(Project project, Media media, String campaignId, LocalDate from, LocalDate to) {
		List<EntityAggregateRow> rows = aggregationRepository.fetchEntityTotals(project.getAdvertiserId(), media,
			campaignId, null, null, from, to);
		MediaPerformanceTotals totals = sumToTotals(media, rows);
		String name = campaignMasterRepository.findById(new CampaignMasterId(project.getAdvertiserId(), media, campaignId))
			.map(CampaignMaster::getLatestName).orElse(campaignId);
		return toEntityPerformance(campaignId, name, totals, filterRate(media));
	}

	private EntityPerformance toEntityPerformance(String id, String name, MediaPerformanceTotals totals, BigDecimal rate) {
		return new EntityPerformance(id, name, totals, calculator.computeRawPerformance(totals),
			calculator.computeSingleOnePerformance(totals, rate));
	}

	private Map<String, MediaPerformanceTotals> groupBy(List<EntityAggregateRow> rows,
			Function<EntityAggregateRow, String> keyFn, Media media) {
		return rows.stream().collect(Collectors.groupingBy(keyFn,
			Collectors.collectingAndThen(Collectors.toList(), list -> sumToTotals(media, list))));
	}

	private MediaPerformanceTotals sumToTotals(Media media, List<EntityAggregateRow> rows) {
		BigDecimal impressions = BigDecimal.ZERO;
		BigDecimal clicks = BigDecimal.ZERO;
		BigDecimal cost = BigDecimal.ZERO;
		BigDecimal rawPurchases = BigDecimal.ZERO;
		BigDecimal rawRevenue = BigDecimal.ZERO;
		for (EntityAggregateRow row : rows) {
			impressions = impressions.add(row.impressions());
			clicks = clicks.add(row.clicks());
			cost = cost.add(row.cost());
			rawPurchases = rawPurchases.add(row.rawPurchases());
			rawRevenue = rawRevenue.add(row.rawRevenue());
		}
		// 하위 계층은 Index를 계산하지 않아 운영일 개념이 필요 없다(PRD 7.6).
		return new MediaPerformanceTotals(media, impressions, clicks, cost, rawPurchases, rawRevenue, 0);
	}

	private BigDecimal filterRate(Media media) {
		return filterRepository.findById(media).orElseThrow().getFilterRate();
	}

	private LocalDate[] previousRange(LocalDate from, LocalDate to) {
		long lengthDays = ChronoUnit.DAYS.between(from, to) + 1;
		LocalDate previousTo = from.minusDays(1);
		LocalDate previousFrom = previousTo.minusDays(lengthDays - 1);
		return new LocalDate[] {previousFrom, previousTo};
	}

	private Page<EntityPerformance> paginate(List<EntityPerformance> all, String search, Pageable pageable) {
		List<EntityPerformance> filtered = all;
		if (search != null && !search.isBlank()) {
			String needle = search.trim().toLowerCase();
			filtered = all.stream()
				.filter(e -> e.id().toLowerCase().contains(needle) || e.name().toLowerCase().contains(needle))
				.toList();
		}
		List<EntityPerformance> sorted = sortEntities(filtered, pageable.getSort());
		int size = clamp(pageable.getPageSize());
		int start = Math.min((int) pageable.getOffset(), sorted.size());
		int end = Math.min(start + size, sorted.size());
		return new PageImpl<>(sorted.subList(start, end), Pageable.ofSize(size).withPage(pageable.getPageNumber()), sorted.size());
	}

	private List<EntityPerformance> sortEntities(List<EntityPerformance> list, Sort sort) {
		Comparator<EntityPerformance> comparator = Comparator.comparing(EntityPerformance::name);
		boolean descending = false;
		if (sort.isSorted()) {
			Sort.Order order = sort.iterator().next();
			comparator = comparatorFor(order.getProperty());
			descending = order.getDirection() == Sort.Direction.DESC;
		}
		return list.stream().sorted(descending ? comparator.reversed() : comparator).toList();
	}

	private Comparator<EntityPerformance> comparatorFor(String property) {
		return switch (property) {
			case "id" -> Comparator.comparing(EntityPerformance::id);
			case "cost" -> Comparator.comparing(e -> e.rawTotals().cost());
			case "impressions" -> Comparator.comparing(e -> e.rawTotals().impressions());
			case "clicks" -> Comparator.comparing(e -> e.rawTotals().clicks());
			case "rawPurchases" -> Comparator.comparing(e -> e.rawTotals().rawPurchases());
			case "singleOnePurchases" -> Comparator.comparing(e -> e.singleOnePerformance().singleOnePurchases());
			case "rawRevenue" -> Comparator.comparing(e -> e.rawTotals().rawRevenue());
			case "singleOneRevenue" -> Comparator.comparing(e -> e.singleOnePerformance().singleOneRevenue());
			default -> Comparator.comparing(EntityPerformance::name);
		};
	}

	private int clamp(int size) {
		return Math.min(Math.max(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1), MAX_PAGE_SIZE);
	}

	private MediaIndexResult findMedia(List<MediaIndexResult> results, Media media) {
		return results.stream().filter(r -> r.media() == media).findFirst()
			.orElseThrow(() -> new ProjectRequestException("프로젝트에 포함되지 않은 매체입니다: " + media));
	}

	private void requireMediaInProject(Project project, Media media) {
		boolean included = projectService.resolveIncludedCampaigns(project).stream()
			.anyMatch(c -> c.getMedia() == media);
		if (!included) {
			throw new ProjectRequestException("프로젝트에 포함되지 않은 매체입니다: " + media);
		}
	}

	private void requireCampaignInProject(Project project, Media media, String campaignId) {
		boolean included = projectService.resolveIncludedCampaigns(project).stream()
			.anyMatch(c -> c.getMedia() == media && c.getCampaignId().equals(campaignId));
		if (!included) {
			throw new ProjectRequestException("프로젝트에 포함되지 않은 캠페인입니다: " + media + " " + campaignId);
		}
	}

	private void requireAdGroupExists(Project project, Media media, String campaignId, String adGroupId) {
		requireCampaignInProject(project, media, campaignId);
		AdGroupMasterId id = new AdGroupMasterId(project.getAdvertiserId(), media, campaignId, adGroupId);
		if (!adGroupMasterRepository.existsById(id)) {
			throw new ProjectRequestException("존재하지 않는 광고그룹입니다: " + adGroupId);
		}
	}

	private Project getProjectOrThrow(Long projectId) {
		return projectRepository.findById(projectId)
			.orElseThrow(() -> new ProjectRequestException("존재하지 않는 프로젝트입니다: " + projectId));
	}

}
