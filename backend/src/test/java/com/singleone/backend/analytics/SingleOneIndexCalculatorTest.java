package com.singleone.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.singleone.backend.domain.common.Media;

/**
 * PRD 8.4/8.5/8.6, 15.3 Golden Index Dataset, AC-02~AC-10 검증. Spring/DB에 의존하지 않는
 * 순수 계산 로직이라 Docker(Testcontainers) 환경과 무관하게 항상 실행된다.
 */
class SingleOneIndexCalculatorTest {

	private final SingleOneIndexCalculator calculator = new SingleOneIndexCalculator();

	// PRD 8.3 고정 테스트 필터율.
	private static final Map<Media, BigDecimal> RATES = Map.of(
		Media.META, new BigDecimal("0.65"),
		Media.TIKTOK, new BigDecimal("0.62"),
		Media.GOOGLE, new BigDecimal("0.69"),
		Media.NAVER, new BigDecimal("0.64"),
		Media.CRITEO, new BigDecimal("0.61"));

	private static MediaPerformanceTotals totals(Media media, long cost, long impressions, long clicks,
			long purchases, long revenue, int operatingDays) {
		return new MediaPerformanceTotals(media, new BigDecimal(impressions), new BigDecimal(clicks),
			new BigDecimal(cost), new BigDecimal(purchases), new BigDecimal(revenue), operatingDays);
	}

	/** PRD 15.3 Golden Index Dataset 원본. 운영일은 조건(&gt;=7)을 넉넉히 만족하는 30으로 둔다. */
	private static Map<Media, MediaPerformanceTotals> goldenDataset() {
		Map<Media, MediaPerformanceTotals> map = new EnumMap<>(Media.class);
		map.put(Media.META, totals(Media.META, 50_000_000, 5_200_000, 115_000, 1280, 195_000_000, 30));
		map.put(Media.TIKTOK, totals(Media.TIKTOK, 35_000_000, 6_000_000, 105_000, 720, 102_000_000, 30));
		map.put(Media.GOOGLE, totals(Media.GOOGLE, 45_000_000, 4_100_000, 130_000, 1350, 210_000_000, 30));
		map.put(Media.NAVER, totals(Media.NAVER, 30_000_000, 3_500_000, 66_000, 610, 90_000_000, 30));
		map.put(Media.CRITEO, totals(Media.CRITEO, 20_000_000, 2_200_000, 35_000, 300, 45_000_000, 30));
		return map;
	}

	@Test
	void goldenDatasetProducesExactIndexScores() {
		Map<Media, MediaPerformanceTotals> dataset = goldenDataset();
		List<MediaIndexResult> results = calculator.calculateIndex(dataset.keySet(), dataset, RATES);
		Map<Media, MediaIndexResult> byMedia = byMedia(results);

		assertGoldenScore(byMedia, Media.GOOGLE, "133.525931", 134);
		assertGoldenScore(byMedia, Media.META, "108.884222", 109);
		assertGoldenScore(byMedia, Media.TIKTOK, "99.183914", 99);
		assertGoldenScore(byMedia, Media.NAVER, "90.429307", 90);
		assertGoldenScore(byMedia, Media.CRITEO, "67.976627", 68);

		BigDecimal average = averageScore(results);
		assertThat(average.setScale(6, RoundingMode.HALF_UP)).isEqualByComparingTo("100.000000");
	}

	// AC-04
	@Test
	void twoValidMediaAverageIsStillHundred() {
		Map<Media, MediaPerformanceTotals> dataset = new EnumMap<>(Media.class);
		dataset.put(Media.META, totals(Media.META, 10_000_000, 1_000_000, 20_000, 100, 20_000_000, 10));
		dataset.put(Media.GOOGLE, totals(Media.GOOGLE, 8_000_000, 1_500_000, 25_000, 80, 15_000_000, 10));

		List<MediaIndexResult> results = calculator.calculateIndex(dataset.keySet(), dataset, RATES);

		assertThat(results).allMatch(r -> r.status() == IndexStatus.VALID);
		assertThat(averageScore(results).setScale(6, RoundingMode.HALF_UP)).isEqualByComparingTo("100.000000");
	}

	// AC-05
	@Test
	void costBelowMinimumMarksInsufficientData() {
		Map<Media, MediaPerformanceTotals> dataset = goldenDataset();
		dataset.put(Media.CRITEO, totals(Media.CRITEO, 999_999, 2_200_000, 35_000, 300, 45_000_000, 30));

		MediaIndexResult criteo = findMedia(calculator.calculateIndex(dataset.keySet(), dataset, RATES), Media.CRITEO);

		assertThat(criteo.status()).isEqualTo(IndexStatus.INSUFFICIENT_DATA);
		assertThat(criteo.indexScore()).isNull();
		assertThat(criteo.singleOnePerformance()).isNotNull();
	}

	// AC-06: 반올림하면 10이 되는 9.76(=16*0.61)로 내부 소수값 기준 판정을 확인한다.
	@Test
	void singleOnePurchasesBelowTenMarksInsufficientData() {
		Map<Media, MediaPerformanceTotals> dataset = goldenDataset();
		dataset.put(Media.CRITEO, totals(Media.CRITEO, 20_000_000, 2_200_000, 35_000, 16, 45_000_000, 30));

		MediaIndexResult criteo = findMedia(calculator.calculateIndex(dataset.keySet(), dataset, RATES), Media.CRITEO);

		assertThat(criteo.status()).isEqualTo(IndexStatus.INSUFFICIENT_DATA);
		assertThat(criteo.singleOnePerformance().singleOnePurchases()).isEqualByComparingTo("9.76");
	}

	// AC-07
	@Test
	void zeroPurchasesExcludedFromIndexWithDashCpaAndZeroRoas() {
		Map<Media, MediaPerformanceTotals> dataset = goldenDataset();
		dataset.put(Media.CRITEO, totals(Media.CRITEO, 20_000_000, 2_200_000, 35_000, 0, 0, 30));

		MediaIndexResult criteo = findMedia(calculator.calculateIndex(dataset.keySet(), dataset, RATES), Media.CRITEO);

		assertThat(criteo.status()).isEqualTo(IndexStatus.INSUFFICIENT_DATA);
		assertThat(criteo.indexScore()).isNull();
		assertThat(criteo.singleOnePerformance().cpa()).isNull();
		assertThat(criteo.singleOnePerformance().roas()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	// AC-08: Criteo는 프로젝트에 포함되지만 기간 내 성과 원본이 전혀 없는 경우.
	@Test
	void missingMediaMarkedAsRequiredDataMissing() {
		Map<Media, MediaPerformanceTotals> dataset = goldenDataset();
		dataset.remove(Media.CRITEO);
		Set<Media> projectMedia = EnumSet.of(Media.META, Media.TIKTOK, Media.GOOGLE, Media.NAVER, Media.CRITEO);

		MediaIndexResult criteo = findMedia(calculator.calculateIndex(projectMedia, dataset, RATES), Media.CRITEO);

		assertThat(criteo.status()).isEqualTo(IndexStatus.MISSING_REQUIRED_DATA);
		assertThat(criteo.singleOnePerformance()).isNull();
		assertThat(criteo.indexScore()).isNull();
	}

	// AC-09
	@Test
	void oneOrFewerValidMediaMarksComparisonInsufficient() {
		Map<Media, MediaPerformanceTotals> dataset = new EnumMap<>(Media.class);
		dataset.put(Media.META, totals(Media.META, 10_000_000, 1_000_000, 20_000, 100, 20_000_000, 10));
		dataset.put(Media.GOOGLE, totals(Media.GOOGLE, 500_000, 200_000, 3_000, 5, 1_000_000, 10));

		List<MediaIndexResult> results = calculator.calculateIndex(dataset.keySet(), dataset, RATES);

		assertThat(findMedia(results, Media.META).status()).isEqualTo(IndexStatus.COMPARISON_MEDIA_INSUFFICIENT);
		assertThat(findMedia(results, Media.META).indexScore()).isNull();
		assertThat(findMedia(results, Media.GOOGLE).status()).isEqualTo(IndexStatus.INSUFFICIENT_DATA);
	}

	// AC-10
	@Test
	void operatingDaysBoundarySixVsSeven() {
		Map<Media, MediaPerformanceTotals> dataset = goldenDataset();
		dataset.put(Media.CRITEO, totals(Media.CRITEO, 20_000_000, 2_200_000, 35_000, 300, 45_000_000, 6));
		assertThat(findMedia(calculator.calculateIndex(dataset.keySet(), dataset, RATES), Media.CRITEO).status())
			.isEqualTo(IndexStatus.INSUFFICIENT_DATA);

		dataset.put(Media.CRITEO, totals(Media.CRITEO, 20_000_000, 2_200_000, 35_000, 300, 45_000_000, 7));
		assertThat(findMedia(calculator.calculateIndex(dataset.keySet(), dataset, RATES), Media.CRITEO).status())
			.isEqualTo(IndexStatus.VALID);
	}

	private static void assertGoldenScore(Map<Media, MediaIndexResult> byMedia, Media media, String expected6dp,
			int expectedRounded) {
		MediaIndexResult result = byMedia.get(media);
		assertThat(result.status()).isEqualTo(IndexStatus.VALID);
		assertThat(result.indexScore().setScale(6, RoundingMode.HALF_UP)).isEqualByComparingTo(expected6dp);
		assertThat(result.indexScore().setScale(0, RoundingMode.HALF_UP).intValueExact()).isEqualTo(expectedRounded);
	}

	private static BigDecimal averageScore(List<MediaIndexResult> results) {
		BigDecimal sum = BigDecimal.ZERO;
		for (MediaIndexResult result : results) {
			sum = sum.add(result.indexScore());
		}
		return sum.divide(new BigDecimal(results.size()), SingleOneIndexCalculator.MC);
	}

	private static Map<Media, MediaIndexResult> byMedia(List<MediaIndexResult> results) {
		Map<Media, MediaIndexResult> map = new EnumMap<>(Media.class);
		results.forEach(r -> map.put(r.media(), r));
		return map;
	}

	private static MediaIndexResult findMedia(List<MediaIndexResult> results, Media media) {
		return results.stream().filter(r -> r.media() == media).findFirst().orElseThrow();
	}

}
