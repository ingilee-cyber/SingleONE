package com.singleone.backend.journey;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.singleone.backend.common.time.TimeUtils;
import com.singleone.backend.domain.common.JourneyEventType;
import com.singleone.backend.domain.common.Media;

/**
 * PRD 9.3(여정 정의)/9.4(프로젝트 적용)/9.6(Linear Attribution)/9.7(Channel Pair) 순수 계산.
 * Spring/DB에 의존하지 않아 Docker 없이도 Golden Journey Dataset(PRD 15.4) 자동 테스트를 항상
 * 실행할 수 있다({@code com.singleone.backend.analytics.SingleOneIndexCalculator}와 동일한 이유).
 */
@Component
public class JourneyAttributionCalculator {

	public static final MathContext MC = MathContext.DECIMAL128;
	private static final int TOP_PATH_LIMIT = 20;
	private static final BigDecimal HUNDRED = new BigDecimal(100);

	private record Journey(Instant purchaseTime, BigDecimal revenue, List<Media> orderedChannels) {
	}

	public JourneyAnalysisResult analyze(List<JourneyEventRecord> events, Set<String> eligibleCampaignKeys,
			LocalDate from, LocalDate to) {
		Map<String, List<JourneyEventRecord>> byUser = events.stream()
			.collect(Collectors.groupingBy(JourneyEventRecord::anonymousUserId));

		List<Journey> attributableJourneys = new ArrayList<>();
		long totalPurchaseJourneys = 0;
		BigDecimal totalPurchaseRevenue = BigDecimal.ZERO;

		for (List<JourneyEventRecord> userEvents : byUser.values()) {
			List<JourneyEventRecord> purchases = userEvents.stream()
				.filter(e -> e.eventType() == JourneyEventType.PURCHASE)
				.sorted(Comparator.comparing(JourneyEventRecord::eventTimestamp))
				.toList();
			List<JourneyEventRecord> eligibleClicks = userEvents.stream()
				.filter(e -> e.eventType() == JourneyEventType.CLICK && isEligibleClick(e, eligibleCampaignKeys))
				.sorted(Comparator.comparing(JourneyEventRecord::eventTimestamp))
				.toList();

			Instant previousPurchaseTime = null;
			for (JourneyEventRecord purchase : purchases) {
				Instant purchaseTime = purchase.eventTimestamp();
				Instant sevenDaysBefore = purchaseTime.minus(Duration.ofDays(7));
				Instant previousPurchaseTimeForThisJourney = previousPurchaseTime;

				// PRD 9.3: 구매 전 7일 이내(AC-34, 경계일 포함) + 직전 구매시점 이후(AC-37, 직전
				// 구매는 재사용하지 않으므로 배타)의 클릭만 이 Journey의 유효 터치포인트다.
				List<Media> orderedChannels = eligibleClicks.stream()
					.filter(click -> !click.eventTimestamp().isBefore(sevenDaysBefore))
					.filter(click -> !click.eventTimestamp().isAfter(purchaseTime))
					.filter(click -> previousPurchaseTimeForThisJourney == null
						|| click.eventTimestamp().isAfter(previousPurchaseTimeForThisJourney))
					.map(JourneyEventRecord::media)
					.toList();

				LocalDate purchaseDate = purchaseTime.atZone(TimeUtils.UPLOAD_DEFAULT_ZONE).toLocalDate();
				if (!purchaseDate.isBefore(from) && !purchaseDate.isAfter(to)) {
					totalPurchaseJourneys++;
					totalPurchaseRevenue = totalPurchaseRevenue.add(purchase.purchaseRevenue());
					// 해석 사항 1: 유효 터치포인트가 없는 구매는 배분할 채널이 없어 Attribution/Pair/
					// Path 결과에서 제외한다(오류가 아니라 채널 없는 Journey를 계산에서 뺀 것).
					if (!orderedChannels.isEmpty()) {
						attributableJourneys.add(new Journey(purchaseTime, purchase.purchaseRevenue(), orderedChannels));
					}
				}
				previousPurchaseTime = purchaseTime;
			}
		}

		long attributedJourneyCount = attributableJourneys.size();
		Map<Media, BigDecimal> attributedPurchases = new EnumMap<>(Media.class);
		Map<Media, BigDecimal> attributedRevenue = new EnumMap<>(Media.class);
		Map<PairKey, long[]> pairCounts = new LinkedHashMap<>();
		Map<PairKey, BigDecimal> pairRevenue = new LinkedHashMap<>();
		Map<List<Media>, long[]> pathCounts = new LinkedHashMap<>();
		Map<List<Media>, BigDecimal> pathRevenue = new LinkedHashMap<>();

		for (Journey journey : attributableJourneys) {
			List<Media> compressedPath = compressConsecutive(journey.orderedChannels());
			pathCounts.computeIfAbsent(compressedPath, k -> new long[1])[0]++;
			pathRevenue.merge(compressedPath, journey.revenue(), BigDecimal::add);

			Set<Media> uniqueChannels = new HashSet<>(journey.orderedChannels());
			BigDecimal share = BigDecimal.ONE.divide(new BigDecimal(uniqueChannels.size()), MC);
			for (Media channel : uniqueChannels) {
				attributedPurchases.merge(channel, share, BigDecimal::add);
				attributedRevenue.merge(channel, journey.revenue().multiply(share, MC), BigDecimal::add);
			}

			if (uniqueChannels.size() >= 2) {
				List<Media> sorted = uniqueChannels.stream().sorted(Comparator.comparingInt(Media::ordinal)).toList();
				for (int i = 0; i < sorted.size(); i++) {
					for (int j = i + 1; j < sorted.size(); j++) {
						PairKey key = new PairKey(sorted.get(i), sorted.get(j));
						pairCounts.computeIfAbsent(key, k -> new long[1])[0]++;
						pairRevenue.merge(key, journey.revenue(), BigDecimal::add);
					}
				}
			}
		}

		List<ChannelAttributionRow> attribution = attributedPurchases.entrySet().stream()
			.map(e -> new ChannelAttributionRow(e.getKey(), e.getValue(), attributedRevenue.get(e.getKey()),
				sharePercent(e.getValue(), attributedJourneyCount)))
			.sorted(Comparator.comparing(ChannelAttributionRow::attributedPurchases).reversed())
			.toList();

		List<ChannelPairRow> channelPairs = pairCounts.entrySet().stream()
			.map(e -> new ChannelPairRow(e.getKey().a(), e.getKey().b(), e.getValue()[0],
				pairRevenue.get(e.getKey()), sharePercent(new BigDecimal(e.getValue()[0]), attributedJourneyCount)))
			.sorted(Comparator.comparingLong(ChannelPairRow::journeyCount).reversed())
			.toList();

		List<TopPath> topPaths = pathCounts.entrySet().stream()
			.map(e -> new TopPath(e.getKey(), e.getValue()[0], pathRevenue.get(e.getKey())))
			.sorted(Comparator.comparingLong(TopPath::purchaseCount).reversed()
				.thenComparing(Comparator.comparing(TopPath::purchaseRevenue).reversed()))
			.limit(TOP_PATH_LIMIT)
			.toList();

		return new JourneyAnalysisResult(topPaths, attribution, channelPairs, totalPurchaseJourneys,
			attributedJourneyCount, totalPurchaseRevenue);
	}

	private static boolean isEligibleClick(JourneyEventRecord click, Set<String> eligibleCampaignKeys) {
		if (click.media() == null || click.campaignId() == null) {
			return false;
		}
		return eligibleCampaignKeys.contains(click.media().name() + "|" + click.campaignId());
	}

	/** PRD 9.3/AC-35: 연속된 동일 채널 클릭은 시각화 경로에서 하나의 노드로 압축한다. */
	private static List<Media> compressConsecutive(List<Media> channels) {
		List<Media> compressed = new ArrayList<>();
		Media last = null;
		for (Media channel : channels) {
			if (channel != last) {
				compressed.add(channel);
				last = channel;
			}
		}
		return compressed;
	}

	private static BigDecimal sharePercent(BigDecimal amount, long denominator) {
		if (denominator == 0) {
			return BigDecimal.ZERO;
		}
		return amount.multiply(HUNDRED, MC).divide(new BigDecimal(denominator), MC);
	}

	/** PRD 9.3/AC-40: 방향 없는 페어. {@link Media} ordinal 오름차순으로 고정해 키를 정규화한다. */
	private record PairKey(Media a, Media b) {
	}

}
