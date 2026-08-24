package com.singleone.backend.journey;

import java.math.BigDecimal;

import com.singleone.backend.domain.common.Media;

/**
 * PRD 9.7: 방향 없는 채널 페어. channelA/channelB는 {@link Media} ordinal 오름차순으로
 * 고정해 Meta+Google과 Google+Meta가 항상 같은 페어로 집계되도록 한다(AC-40).
 */
public record ChannelPairRow(
	Media channelA,
	Media channelB,
	long journeyCount,
	BigDecimal purchaseRevenue,
	BigDecimal sharePercent
) {
}
