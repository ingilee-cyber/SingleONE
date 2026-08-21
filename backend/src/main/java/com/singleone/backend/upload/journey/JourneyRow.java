package com.singleone.backend.upload.journey;

import java.math.BigDecimal;
import java.time.Instant;

import com.singleone.backend.domain.common.JourneyEventType;
import com.singleone.backend.domain.common.Media;

/**
 * PRD 9.5 이벤트 스키마. CLICK/PURCHASE에 따라 일부 필드가 비어 있을 수 있다.
 */
public record JourneyRow(
	String eventId,
	String advertiserId,
	String anonymousUserId,
	Instant eventTimestamp,
	JourneyEventType eventType,
	Media media,
	String campaignId,
	String adGroupId,
	String adId,
	String orderId,
	BigDecimal purchaseRevenue
) {

	public String eventNaturalKey() {
		return advertiserId + "|" + eventId;
	}

}
