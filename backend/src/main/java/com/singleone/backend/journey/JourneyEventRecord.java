package com.singleone.backend.journey;

import java.math.BigDecimal;
import java.time.Instant;

import com.singleone.backend.domain.common.JourneyEventType;
import com.singleone.backend.domain.common.Media;

/**
 * PRD 9.3/9.5 이벤트를 계산기 입력용으로 평탄화한 record. CLICK/PURCHASE에 따라
 * media/campaignId 또는 orderId/purchaseRevenue가 null일 수 있다.
 */
public record JourneyEventRecord(
	String eventId,
	String anonymousUserId,
	Instant eventTimestamp,
	JourneyEventType eventType,
	Media media,
	String campaignId,
	String orderId,
	BigDecimal purchaseRevenue
) {
}
