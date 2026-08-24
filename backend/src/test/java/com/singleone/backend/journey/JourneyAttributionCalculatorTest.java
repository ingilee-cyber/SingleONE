package com.singleone.backend.journey;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.singleone.backend.domain.common.JourneyEventType;
import com.singleone.backend.domain.common.Media;

/**
 * PRD 9.3~9.7, 15.4 Golden Journey Dataset, AC-34~AC-42 검증. Spring/DB에 의존하지 않는 순수
 * 계산 로직이라 Docker(Testcontainers) 환경과 무관하게 항상 실행된다.
 */
class JourneyAttributionCalculatorTest {

	private final JourneyAttributionCalculator calculator = new JourneyAttributionCalculator();

	private static final Set<String> ELIGIBLE = Set.of("META|c1", "GOOGLE|c1", "TIKTOK|c1");
	private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
	private static final LocalDate TO = LocalDate.of(2026, 7, 10);

	private static JourneyEventRecord click(String user, Media media, String campaignId, Instant time) {
		return new JourneyEventRecord("click-" + user + "-" + media + "-" + time, user, time,
			JourneyEventType.CLICK, media, campaignId, null, null);
	}

	private static JourneyEventRecord purchase(String user, Instant time, long revenue) {
		return new JourneyEventRecord("purchase-" + user + "-" + time, user, time,
			JourneyEventType.PURCHASE, null, null, "order-" + user + "-" + time, new BigDecimal(revenue));
	}

	/** PRD 15.4 Golden Journey Dataset. 모든 구매는 2026-07-10(선택 기간 내), 클릭은 그 이전 며칠 이내다. */
	private static List<JourneyEventRecord> goldenDataset() {
		return List.of(
			click("U001", Media.META, "c1", Instant.parse("2026-07-08T10:00:00Z")),
			click("U001", Media.GOOGLE, "c1", Instant.parse("2026-07-09T10:00:00Z")),
			purchase("U001", Instant.parse("2026-07-10T10:00:00Z"), 100_000),

			click("U002", Media.TIKTOK, "c1", Instant.parse("2026-07-08T11:00:00Z")),
			click("U002", Media.META, "c1", Instant.parse("2026-07-09T11:00:00Z")),
			purchase("U002", Instant.parse("2026-07-10T11:00:00Z"), 120_000),

			click("U003", Media.GOOGLE, "c1", Instant.parse("2026-07-09T12:00:00Z")),
			purchase("U003", Instant.parse("2026-07-10T12:00:00Z"), 80_000),

			click("U004", Media.META, "c1", Instant.parse("2026-07-08T09:00:00Z")),
			click("U004", Media.GOOGLE, "c1", Instant.parse("2026-07-08T15:00:00Z")),
			click("U004", Media.TIKTOK, "c1", Instant.parse("2026-07-09T09:00:00Z")),
			purchase("U004", Instant.parse("2026-07-10T09:00:00Z"), 150_000));
	}

	private static BigDecimal round6(BigDecimal value) {
		return value.setScale(6, RoundingMode.HALF_UP);
	}

	@Test
	void goldenJourneyDatasetProducesExpectedLinearAttribution() {
		JourneyAnalysisResult result = calculator.analyze(goldenDataset(), ELIGIBLE, FROM, TO);

		assertThat(result.attributedJourneyCount()).isEqualTo(4);
		assertThat(result.totalPurchaseJourneys()).isEqualTo(4);
		assertThat(result.totalPurchaseRevenue()).isEqualByComparingTo("450000");

		assertThat(result.attribution()).hasSize(3);
		var google = result.attribution().stream().filter(r -> r.channel() == Media.GOOGLE).findFirst().orElseThrow();
		var meta = result.attribution().stream().filter(r -> r.channel() == Media.META).findFirst().orElseThrow();
		var tiktok = result.attribution().stream().filter(r -> r.channel() == Media.TIKTOK).findFirst().orElseThrow();

		assertThat(round6(google.attributedPurchases())).isEqualByComparingTo("1.833333");
		assertThat(round6(meta.attributedPurchases())).isEqualByComparingTo("1.333333");
		assertThat(round6(tiktok.attributedPurchases())).isEqualByComparingTo("0.833333");
		BigDecimal sum = google.attributedPurchases().add(meta.attributedPurchases()).add(tiktok.attributedPurchases());
		assertThat(round6(sum)).isEqualByComparingTo("4.000000");

		// Google: 100000/2(U001) + 80000(U003) + 150000/3(U004) = 180000
		assertThat(google.attributedRevenue().setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("180000.00");
	}

	@Test
	void goldenJourneyDatasetProducesExpectedChannelPairs() {
		JourneyAnalysisResult result = calculator.analyze(goldenDataset(), ELIGIBLE, FROM, TO);

		assertThat(result.channelPairs()).hasSize(3);
		long metaGoogle = pairCount(result, Media.META, Media.GOOGLE);
		long metaTiktok = pairCount(result, Media.META, Media.TIKTOK);
		long googleTiktok = pairCount(result, Media.GOOGLE, Media.TIKTOK);

		assertThat(metaGoogle).isEqualTo(2);
		assertThat(metaTiktok).isEqualTo(2);
		assertThat(googleTiktok).isEqualTo(1);
	}

	private static long pairCount(JourneyAnalysisResult result, Media a, Media b) {
		return result.channelPairs().stream()
			.filter(p -> (p.channelA() == a && p.channelB() == b) || (p.channelA() == b && p.channelB() == a))
			.findFirst().orElseThrow().journeyCount();
	}

	@Test
	void ac34_clickExactlySevenDaysBeforeIsIncludedButEightDaysBeforeIsExcluded() {
		Instant purchaseTime = Instant.parse("2026-07-10T10:00:00Z");
		JourneyEventRecord withinWindow = click("U010", Media.META, "c1", purchaseTime.minusSeconds(7 * 86400));
		JourneyEventRecord tooOld = click("U010", Media.GOOGLE, "c1", purchaseTime.minusSeconds(8 * 86400));
		List<JourneyEventRecord> events = List.of(withinWindow, tooOld, purchase("U010", purchaseTime, 10_000));

		JourneyAnalysisResult result = calculator.analyze(events, ELIGIBLE, FROM, TO);

		assertThat(result.attribution()).hasSize(1);
		assertThat(result.attribution().get(0).channel()).isEqualTo(Media.META);
	}

	@Test
	void ac35_consecutiveSameChannelClicksCompressToOneNodeInTopPath() {
		Instant purchaseTime = Instant.parse("2026-07-10T10:00:00Z");
		List<JourneyEventRecord> events = List.of(
			click("U011", Media.META, "c1", purchaseTime.minusSeconds(3 * 3600)),
			click("U011", Media.META, "c1", purchaseTime.minusSeconds(2 * 3600)),
			click("U011", Media.GOOGLE, "c1", purchaseTime.minusSeconds(1 * 3600)),
			purchase("U011", purchaseTime, 10_000));

		JourneyAnalysisResult result = calculator.analyze(events, ELIGIBLE, FROM, TO);

		assertThat(result.topPaths()).hasSize(1);
		assertThat(result.topPaths().get(0).channels()).containsExactly(Media.META, Media.GOOGLE);
	}

	@Test
	void ac36_nonConsecutiveRepeatedChannelStillCountsOnceForAttribution() {
		Instant purchaseTime = Instant.parse("2026-07-10T10:00:00Z");
		List<JourneyEventRecord> events = List.of(
			click("U012", Media.META, "c1", purchaseTime.minusSeconds(3 * 3600)),
			click("U012", Media.GOOGLE, "c1", purchaseTime.minusSeconds(2 * 3600)),
			click("U012", Media.META, "c1", purchaseTime.minusSeconds(1 * 3600)),
			purchase("U012", purchaseTime, 10_000));

		JourneyAnalysisResult result = calculator.analyze(events, ELIGIBLE, FROM, TO);

		var meta = result.attribution().stream().filter(r -> r.channel() == Media.META).findFirst().orElseThrow();
		var google = result.attribution().stream().filter(r -> r.channel() == Media.GOOGLE).findFirst().orElseThrow();
		assertThat(round6(meta.attributedPurchases())).isEqualByComparingTo("0.500000");
		assertThat(round6(google.attributedPurchases())).isEqualByComparingTo("0.500000");
		// 압축 전 raw 순서(Meta,Google,Meta)가 그대로 path에 남아 3개 노드여야 한다(연속이 아니므로 미압축).
		assertThat(result.topPaths().get(0).channels()).containsExactly(Media.META, Media.GOOGLE, Media.META);
	}

	@Test
	void ac37_clickBeforeAnEarlierPurchaseIsNotReusedByTheNextJourney() {
		List<JourneyEventRecord> events = List.of(
			click("U013", Media.META, "c1", Instant.parse("2026-07-01T00:00:00Z")),
			purchase("U013", Instant.parse("2026-07-02T00:00:00Z"), 10_000),
			// 두 번째 구매는 META 클릭이 없다(META 클릭은 직전 구매보다 이전이라 재사용 불가) — GOOGLE만 유효.
			click("U013", Media.GOOGLE, "c1", Instant.parse("2026-07-03T00:00:00Z")),
			purchase("U013", Instant.parse("2026-07-04T00:00:00Z"), 20_000));

		JourneyAnalysisResult result = calculator.analyze(events, ELIGIBLE, FROM, TO);

		assertThat(result.attributedJourneyCount()).isEqualTo(2);
		var meta = result.attribution().stream().filter(r -> r.channel() == Media.META).findFirst().orElseThrow();
		var google = result.attribution().stream().filter(r -> r.channel() == Media.GOOGLE).findFirst().orElseThrow();
		assertThat(round6(meta.attributedPurchases())).isEqualByComparingTo("1.000000");
		assertThat(round6(google.attributedPurchases())).isEqualByComparingTo("1.000000");
	}

	@Test
	void ac38_clickForCampaignNotInProjectIsExcluded() {
		Instant purchaseTime = Instant.parse("2026-07-10T10:00:00Z");
		List<JourneyEventRecord> events = List.of(
			click("U014", Media.META, "outside-campaign", purchaseTime.minusSeconds(3600)),
			click("U014", Media.GOOGLE, "c1", purchaseTime.minusSeconds(1800)),
			purchase("U014", purchaseTime, 10_000));

		JourneyAnalysisResult result = calculator.analyze(events, ELIGIBLE, FROM, TO);

		assertThat(result.attribution()).hasSize(1);
		assertThat(result.attribution().get(0).channel()).isEqualTo(Media.GOOGLE);
	}

	@Test
	void ac40And41_unorderedPairsAndThreeChannelJourneyProducesThreePairs() {
		Instant purchaseTime = Instant.parse("2026-07-10T10:00:00Z");
		List<JourneyEventRecord> events = List.of(
			click("U015", Media.GOOGLE, "c1", purchaseTime.minusSeconds(3 * 3600)),
			click("U015", Media.META, "c1", purchaseTime.minusSeconds(2 * 3600)),
			click("U015", Media.TIKTOK, "c1", purchaseTime.minusSeconds(1 * 3600)),
			purchase("U015", purchaseTime, 30_000));

		JourneyAnalysisResult result = calculator.analyze(events, ELIGIBLE, FROM, TO);

		assertThat(result.channelPairs()).hasSize(3);
		assertThat(pairCount(result, Media.META, Media.GOOGLE)).isEqualTo(1);
		assertThat(pairCount(result, Media.META, Media.TIKTOK)).isEqualTo(1);
		assertThat(pairCount(result, Media.GOOGLE, Media.TIKTOK)).isEqualTo(1);
	}

	@Test
	void purchaseWithNoEligibleTouchpointsIsExcludedFromAttributionButCountedInTotals() {
		Instant purchaseTime = Instant.parse("2026-07-10T10:00:00Z");
		List<JourneyEventRecord> events = List.of(purchase("U016", purchaseTime, 5_000));

		JourneyAnalysisResult result = calculator.analyze(events, ELIGIBLE, FROM, TO);

		assertThat(result.attributedJourneyCount()).isZero();
		assertThat(result.attribution()).isEmpty();
		assertThat(result.totalPurchaseJourneys()).isEqualTo(1);
		assertThat(result.totalPurchaseRevenue()).isEqualByComparingTo("5000");
	}

	@Test
	void ac55_topPathsAreLimitedToTwentyRankedByPurchaseCountDescending() {
		// 서로 다른 21개 경로를 만들어(단일 채널 5개 + 2채널 순서쌍 16개) 각각 (21-i)번 구매수를 부여한다.
		// 가장 적은 구매수(1건)를 가진 경로 하나만 Top 20에서 잘려야 한다(AC-55).
		Media[] media = Media.values();
		List<List<Media>> distinctPaths = new java.util.ArrayList<>();
		for (Media m : media) {
			distinctPaths.add(List.of(m));
		}
		outer:
		for (Media a : media) {
			for (Media b : media) {
				if (a == b) {
					continue;
				}
				distinctPaths.add(List.of(a, b));
				if (distinctPaths.size() >= 21) {
					break outer;
				}
			}
		}
		assertThat(distinctPaths).hasSizeGreaterThanOrEqualTo(21);

		Set<String> eligibleAll = Set.of("META|c1", "TIKTOK|c1", "GOOGLE|c1", "NAVER|c1", "CRITEO|c1");
		List<JourneyEventRecord> events = new java.util.ArrayList<>();
		int userSeq = 0;
		for (int pathIndex = 0; pathIndex < 21; pathIndex++) {
			List<Media> path = distinctPaths.get(pathIndex);
			int journeyCount = 21 - pathIndex; // path 0 -> 21건(최다), path 20 -> 1건(최소, 잘려야 함)
			for (int j = 0; j < journeyCount; j++) {
				String user = "U" + (userSeq++);
				Instant purchaseTime = Instant.parse("2026-07-10T10:00:00Z");
				for (int step = 0; step < path.size(); step++) {
					events.add(click(user, path.get(step), "c1", purchaseTime.minusSeconds((path.size() - step) * 3600L)));
				}
				events.add(purchase(user, purchaseTime, 1_000));
			}
		}

		JourneyAnalysisResult result = calculator.analyze(events, eligibleAll, FROM, TO);

		assertThat(result.topPaths()).hasSize(20);
		// 가장 적은(1건) 경로(distinctPaths.get(20))는 Top 20에서 제외돼야 한다.
		List<Media> excludedPath = distinctPaths.get(20);
		assertThat(result.topPaths()).noneMatch(p -> p.channels().equals(excludedPath));
		// 구매수 내림차순 정렬 확인.
		for (int i = 0; i < result.topPaths().size() - 1; i++) {
			assertThat(result.topPaths().get(i).purchaseCount()).isGreaterThanOrEqualTo(result.topPaths().get(i + 1).purchaseCount());
		}
		assertThat(result.topPaths().get(0).purchaseCount()).isEqualTo(21);
	}

}
