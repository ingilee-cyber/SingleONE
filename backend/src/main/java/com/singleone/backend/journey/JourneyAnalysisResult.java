package com.singleone.backend.journey;

import java.math.BigDecimal;
import java.util.List;

/**
 * PRD 9장 Journey & Attribution 화면 3개 탭이 공유하는 계산 결과 전체.
 * totalPurchaseJourneys는 기간 내 전체 구매 건수(터치포인트 유무 무관), attributedJourneyCount는
 * 그중 유효 터치포인트가 있어 attribution/pair 계산에 실제로 포함된 건수다(해석 사항 1번).
 */
public record JourneyAnalysisResult(
	List<TopPath> topPaths,
	List<ChannelAttributionRow> attribution,
	List<ChannelPairRow> channelPairs,
	long totalPurchaseJourneys,
	long attributedJourneyCount,
	BigDecimal totalPurchaseRevenue
) {
}
