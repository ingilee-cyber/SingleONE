package com.singleone.backend.upload.journey;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.singleone.backend.common.time.TimeUtils;
import com.singleone.backend.domain.common.JourneyEventType;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.upload.RowValidationError;
import com.singleone.backend.upload.parse.RawRow;

/**
 * PRD 9.5(이벤트 스키마), 11.5(검증 규칙)에 따라 한 행을 검증하고 파싱한다.
 * CLICK은 media/campaign_id가 필수이고, PURCHASE는 order_id/purchase_revenue가 필수이며
 * media/campaign_id/ad_group_id/ad_id는 비어 있어야 한다.
 */
public final class JourneyRowParser {

	private JourneyRowParser() {
	}

	public record Result(JourneyRow row, List<RowValidationError> errors) {
		public boolean isValid() {
			return errors.isEmpty();
		}
	}

	public static Result parse(RawRow raw, String expectedAdvertiserId) {
		List<RowValidationError> errors = new ArrayList<>();
		long rowNo = raw.rowNo();

		String eventId = requireField(raw, "event_id", errors);
		String anonymousUserId = requireField(raw, "anonymous_user_id", errors);
		String advertiserId = requireField(raw, "advertiser_id", errors);
		String eventTypeRaw = requireField(raw, "event_type", errors);
		Instant eventTimestamp = parseTimestamp(raw, "event_timestamp", errors);

		if (advertiserId != null && expectedAdvertiserId != null && !advertiserId.equals(expectedAdvertiserId)) {
			errors.add(new RowValidationError(rowNo, "ADVERTISER_MISMATCH",
				"업로드 요청의 광고주(%s)와 행의 advertiser_id(%s)가 다릅니다.".formatted(expectedAdvertiserId, advertiserId)));
		}

		JourneyEventType eventType = null;
		if (eventTypeRaw != null) {
			eventType = parseEventType(eventTypeRaw);
			if (eventType == null) {
				errors.add(new RowValidationError(rowNo, "INVALID_EVENT_TYPE", "event_type은 CLICK/PURCHASE만 허용됩니다: " + eventTypeRaw));
			}
		}

		if (eventType == null) {
			return new Result(null, errors);
		}

		return switch (eventType) {
			case CLICK -> parseClick(raw, eventId, advertiserId, anonymousUserId, eventTimestamp, errors);
			case PURCHASE -> parsePurchase(raw, eventId, advertiserId, anonymousUserId, eventTimestamp, errors);
		};
	}

	private static Result parseClick(RawRow raw, String eventId, String advertiserId, String anonymousUserId,
			Instant eventTimestamp, List<RowValidationError> errors) {
		String mediaRaw = requireField(raw, "media", errors);
		String campaignId = requireField(raw, "campaign_id", errors);
		String adGroupId = raw.get("ad_group_id");
		String adId = raw.get("ad_id");

		Media media = null;
		if (mediaRaw != null) {
			media = parseMedia(mediaRaw);
			if (media == null) {
				errors.add(new RowValidationError(raw.rowNo(), "UNSUPPORTED_MEDIA", "지원하지 않는 media 입니다: " + mediaRaw));
			}
		}

		if (!errors.isEmpty()) {
			return new Result(null, errors);
		}

		JourneyRow row = new JourneyRow(eventId, advertiserId, anonymousUserId, eventTimestamp,
			JourneyEventType.CLICK, media, campaignId, adGroupId, adId, null, null);
		return new Result(row, errors);
	}

	private static Result parsePurchase(RawRow raw, String eventId, String advertiserId, String anonymousUserId,
			Instant eventTimestamp, List<RowValidationError> errors) {
		String orderId = requireField(raw, "order_id", errors);
		BigDecimal purchaseRevenue = parseNonNegativeDecimal(raw, "purchase_revenue", errors);

		if (!errors.isEmpty()) {
			return new Result(null, errors);
		}

		JourneyRow row = new JourneyRow(eventId, advertiserId, anonymousUserId, eventTimestamp,
			JourneyEventType.PURCHASE, null, null, null, null, orderId, purchaseRevenue);
		return new Result(row, errors);
	}

	private static String requireField(RawRow raw, String column, List<RowValidationError> errors) {
		String value = raw.get(column);
		if (value == null || value.isEmpty()) {
			errors.add(new RowValidationError(raw.rowNo(), "REQUIRED_FIELD_MISSING", "필수 컬럼이 비어 있습니다: " + column));
			return null;
		}
		return value;
	}

	private static Instant parseTimestamp(RawRow raw, String column, List<RowValidationError> errors) {
		String value = requireField(raw, column, errors);
		if (value == null) {
			return null;
		}
		try {
			return TimeUtils.parseUploadTimestamp(value);
		} catch (DateTimeParseException e) {
			errors.add(new RowValidationError(raw.rowNo(), "INVALID_TIMESTAMP", "event_timestamp 형식이 올바르지 않습니다: " + value));
			return null;
		}
	}

	private static BigDecimal parseNonNegativeDecimal(RawRow raw, String column, List<RowValidationError> errors) {
		String value = requireField(raw, column, errors);
		if (value == null) {
			return null;
		}
		try {
			BigDecimal parsed = new BigDecimal(value);
			if (parsed.signum() < 0) {
				errors.add(new RowValidationError(raw.rowNo(), "NEGATIVE_VALUE", column + " 값은 음수일 수 없습니다: " + value));
				return null;
			}
			return parsed;
		} catch (NumberFormatException e) {
			errors.add(new RowValidationError(raw.rowNo(), "INVALID_NUMBER", column + " 값이 숫자가 아닙니다: " + value));
			return null;
		}
	}

	private static Media parseMedia(String value) {
		try {
			return Media.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static JourneyEventType parseEventType(String value) {
		try {
			return JourneyEventType.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

}
