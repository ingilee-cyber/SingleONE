package com.singleone.backend.analytics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.singleone.backend.domain.common.Media;

/**
 * PRD 8.4(SingleONE 성과)/8.5(Index 공식)/8.6(최소 조건) 계산 로직. Spring/DB에 의존하지 않는
 * 순수 계산이라 Docker 없이도 Golden Dataset(PRD 15.3) 자동 테스트를 항상 실행할 수 있다.
 */
@Component
public class SingleOneIndexCalculator {

	public static final MathContext MC = MathContext.DECIMAL128;

	private static final BigDecimal MIN_COST = new BigDecimal("1000000");
	private static final BigDecimal MIN_PURCHASES = new BigDecimal("10");
	private static final int MIN_OPERATING_DAYS = 7;
	private static final BigDecimal HUNDRED = new BigDecimal(100);

	private static final BigDecimal WEIGHT_EXPOSURE = new BigDecimal("0.10");
	private static final BigDecimal WEIGHT_CLICK = new BigDecimal("0.20");
	private static final BigDecimal WEIGHT_PURCHASE = new BigDecimal("0.35");
	private static final BigDecimal WEIGHT_REVENUE = new BigDecimal("0.35");

	/** PRD 8.4: SingleONE 성과 + 효율 참고 지표(CPA/ROAS, PRD 8.7). */
	public SingleOnePerformance computeSingleOnePerformance(MediaPerformanceTotals totals, BigDecimal filterRate) {
		BigDecimal singleOnePurchases = totals.rawPurchases().multiply(filterRate, MC);
		BigDecimal singleOneRevenue = totals.rawRevenue().multiply(filterRate, MC);

		BigDecimal cpa;
		BigDecimal roas;
		if (totals.cost().signum() == 0) {
			// PRD 미명시 방어적 처리: cost=0이면 0으로 나눌 수 없어 CPA/ROAS 모두 계산하지 않는다.
			cpa = null;
			roas = null;
		} else if (singleOnePurchases.signum() == 0) {
			cpa = null;
			roas = BigDecimal.ZERO;
		} else {
			cpa = totals.cost().divide(singleOnePurchases, MC);
			roas = singleOneRevenue.divide(totals.cost(), MC).multiply(HUNDRED, MC);
		}
		return new SingleOnePerformance(totals.media(), singleOnePurchases, singleOneRevenue, cpa, roas);
	}

	/**
	 * PRD 8.5/8.6: projectMedia는 프로젝트에 포함된 전체 매체, observed는 그중 기간 내 성과 원본이
	 * 존재하는 매체만 담는다(없는 매체는 MISSING_REQUIRED_DATA로 분류됨).
	 */
	public List<MediaIndexResult> calculateIndex(Set<Media> projectMedia, Map<Media, MediaPerformanceTotals> observed,
			Map<Media, BigDecimal> filterRates) {
		Map<Media, IndexStatus> status = new EnumMap<>(Media.class);
		Map<Media, SingleOnePerformance> performanceByMedia = new EnumMap<>(Media.class);

		for (Media media : projectMedia) {
			MediaPerformanceTotals totals = observed.get(media);
			if (totals == null) {
				status.put(media, IndexStatus.MISSING_REQUIRED_DATA);
				continue;
			}
			SingleOnePerformance performance = computeSingleOnePerformance(totals, filterRates.get(media));
			performanceByMedia.put(media, performance);
			boolean meetsMinimum = totals.operatingDays() >= MIN_OPERATING_DAYS
				&& totals.cost().compareTo(MIN_COST) >= 0
				&& performance.singleOnePurchases().compareTo(MIN_PURCHASES) >= 0;
			status.put(media, meetsMinimum ? IndexStatus.VALID : IndexStatus.INSUFFICIENT_DATA);
		}

		List<Media> tentativelyValid = new ArrayList<>();
		for (Map.Entry<Media, IndexStatus> entry : status.entrySet()) {
			if (entry.getValue() == IndexStatus.VALID) {
				tentativelyValid.add(entry.getKey());
			}
		}

		if (tentativelyValid.size() <= 1) {
			for (Media media : tentativelyValid) {
				status.put(media, IndexStatus.COMPARISON_MEDIA_INSUFFICIENT);
			}
			return buildResults(projectMedia, status, performanceByMedia, Map.of());
		}

		Map<Media, BigDecimal> exposureEff = new EnumMap<>(Media.class);
		Map<Media, BigDecimal> clickEff = new EnumMap<>(Media.class);
		Map<Media, BigDecimal> purchaseEff = new EnumMap<>(Media.class);
		Map<Media, BigDecimal> revenueEff = new EnumMap<>(Media.class);

		for (Media media : tentativelyValid) {
			MediaPerformanceTotals totals = observed.get(media);
			SingleOnePerformance performance = performanceByMedia.get(media);
			BigDecimal cost = totals.cost();
			exposureEff.put(media, totals.impressions().divide(cost, MC));
			clickEff.put(media, totals.clicks().divide(cost, MC));
			purchaseEff.put(media, performance.singleOnePurchases().divide(cost, MC));
			revenueEff.put(media, performance.singleOneRevenue().divide(cost, MC));
		}

		BigDecimal meanExposure = mean(exposureEff.values());
		BigDecimal meanClick = mean(clickEff.values());
		BigDecimal meanPurchase = mean(purchaseEff.values());
		BigDecimal meanRevenue = mean(revenueEff.values());

		Map<Media, BigDecimal> scores = new EnumMap<>(Media.class);
		for (Media media : tentativelyValid) {
			BigDecimal exposureIdx = exposureEff.get(media).divide(meanExposure, MC).multiply(HUNDRED, MC);
			BigDecimal clickIdx = clickEff.get(media).divide(meanClick, MC).multiply(HUNDRED, MC);
			BigDecimal purchaseIdx = purchaseEff.get(media).divide(meanPurchase, MC).multiply(HUNDRED, MC);
			BigDecimal revenueIdx = revenueEff.get(media).divide(meanRevenue, MC).multiply(HUNDRED, MC);
			BigDecimal score = exposureIdx.multiply(WEIGHT_EXPOSURE, MC)
				.add(clickIdx.multiply(WEIGHT_CLICK, MC))
				.add(purchaseIdx.multiply(WEIGHT_PURCHASE, MC))
				.add(revenueIdx.multiply(WEIGHT_REVENUE, MC));
			scores.put(media, score);
		}

		return buildResults(projectMedia, status, performanceByMedia, scores);
	}

	/**
	 * 이미 조회된 일자×매체 데이터에서 [from, to] 구간만 합산한다. 기간 합산(8.4)과 7일 Rolling
	 * window(8.9)가 이 메서드를 공통으로 재사용한다. 해당 구간에 행이 전혀 없는 매체는 결과에 포함하지
	 * 않는다(호출자가 MISSING_REQUIRED_DATA로 해석).
	 */
	public Map<Media, MediaPerformanceTotals> aggregateWindow(List<DailyMediaTotal> daily, Set<Media> projectMedia,
			LocalDate from, LocalDate to) {
		Map<Media, BigDecimal> impressions = new EnumMap<>(Media.class);
		Map<Media, BigDecimal> clicks = new EnumMap<>(Media.class);
		Map<Media, BigDecimal> cost = new EnumMap<>(Media.class);
		Map<Media, BigDecimal> purchases = new EnumMap<>(Media.class);
		Map<Media, BigDecimal> revenue = new EnumMap<>(Media.class);
		Map<Media, Integer> operatingDays = new EnumMap<>(Media.class);

		for (DailyMediaTotal row : daily) {
			if (!projectMedia.contains(row.media()) || row.date().isBefore(from) || row.date().isAfter(to)) {
				continue;
			}
			impressions.merge(row.media(), row.impressions(), BigDecimal::add);
			clicks.merge(row.media(), row.clicks(), BigDecimal::add);
			cost.merge(row.media(), row.cost(), BigDecimal::add);
			purchases.merge(row.media(), row.rawPurchases(), BigDecimal::add);
			revenue.merge(row.media(), row.rawRevenue(), BigDecimal::add);
			if (row.cost().signum() > 0) {
				operatingDays.merge(row.media(), 1, Integer::sum);
			}
		}

		Map<Media, MediaPerformanceTotals> result = new EnumMap<>(Media.class);
		for (Media media : projectMedia) {
			if (!impressions.containsKey(media)) {
				continue;
			}
			result.put(media, new MediaPerformanceTotals(media, impressions.get(media), clicks.get(media),
				cost.get(media), purchases.get(media), revenue.get(media), operatingDays.getOrDefault(media, 0)));
		}
		return result;
	}

	private static BigDecimal mean(Collection<BigDecimal> values) {
		BigDecimal sum = BigDecimal.ZERO;
		for (BigDecimal value : values) {
			sum = sum.add(value);
		}
		return sum.divide(new BigDecimal(values.size()), MC);
	}

	private List<MediaIndexResult> buildResults(Set<Media> projectMedia, Map<Media, IndexStatus> status,
			Map<Media, SingleOnePerformance> performanceByMedia, Map<Media, BigDecimal> scores) {
		List<MediaIndexResult> results = new ArrayList<>();
		for (Media media : projectMedia) {
			results.add(new MediaIndexResult(media, status.get(media), performanceByMedia.get(media), scores.get(media)));
		}
		return results;
	}

}
