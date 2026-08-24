package com.singleone.backend.journey;

import java.math.BigDecimal;

import com.singleone.backend.domain.common.Media;

/**
 * PRD 9.6 Linear Attribution 결과 한 채널분. "SingleONE 기여 구매"라는 표현은 이 record가
 * 아니라 Frontend 라벨링 단계에서 지켜야 할 제약이다(계산 자체는 무관).
 */
public record ChannelAttributionRow(
	Media channel,
	BigDecimal attributedPurchases,
	BigDecimal attributedRevenue,
	BigDecimal sharePercent
) {
}
