package com.singleone.backend.upload.performance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.singleone.backend.domain.common.Media;
import com.singleone.backend.upload.RowValidationError;
import com.singleone.backend.upload.parse.RawRow;

/**
 * PRD 11.2(필드), 11.5(검증 규칙)에 따라 한 행을 검증하고 파싱한다.
 * advertiser_id는 업로드 요청에 명시된 광고주와 일치해야 한다 (사용자 확인: 파일 1개 = 광고주 1명).
 */
public final class PerformanceRowParser {

	private PerformanceRowParser() {
	}

	public record Result(PerformanceRow row, List<RowValidationError> errors) {
		public boolean isValid() {
			return errors.isEmpty();
		}
	}

	public static Result parse(RawRow raw, String expectedAdvertiserId) {
		List<RowValidationError> errors = new ArrayList<>();
		long rowNo = raw.rowNo();

		String advertiserId = requireField(raw, "advertiser_id", errors);
		String advertiserName = requireField(raw, "advertiser_name", errors);
		String mediaRaw = requireField(raw, "media", errors);
		String campaignId = requireField(raw, "campaign_id", errors);
		String campaignName = requireField(raw, "campaign_name", errors);
		String adGroupId = requireField(raw, "ad_group_id", errors);
		String adGroupName = requireField(raw, "ad_group_name", errors);
		String adId = requireField(raw, "ad_id", errors);
		String adName = requireField(raw, "ad_name", errors);

		LocalDate date = parseDate(raw, "date", errors);
		Long impressions = parseNonNegativeLong(raw, "impressions", errors);
		Long clicks = parseNonNegativeLong(raw, "clicks", errors);
		BigDecimal cost = parseNonNegativeDecimal(raw, "cost", errors);
		Long addToCart = parseNonNegativeLong(raw, "add_to_cart", errors);
		Long purchases = parseNonNegativeLong(raw, "purchases", errors);
		BigDecimal purchaseRevenue = parseNonNegativeDecimal(raw, "purchase_revenue", errors);

		Media media = null;
		if (mediaRaw != null) {
			media = parseMedia(mediaRaw);
			if (media == null) {
				errors.add(new RowValidationError(rowNo, "UNSUPPORTED_MEDIA", "지원하지 않는 media 입니다: " + mediaRaw));
			}
		}

		if (advertiserId != null && expectedAdvertiserId != null && !advertiserId.equals(expectedAdvertiserId)) {
			errors.add(new RowValidationError(rowNo, "ADVERTISER_MISMATCH",
				"업로드 요청의 광고주(%s)와 행의 advertiser_id(%s)가 다릅니다.".formatted(expectedAdvertiserId, advertiserId)));
		}

		if (!errors.isEmpty()) {
			return new Result(null, errors);
		}

		PerformanceRow row = new PerformanceRow(
			date, advertiserId, advertiserName, media, campaignId, campaignName,
			adGroupId, adGroupName, adId, adName,
			impressions, clicks, cost, addToCart, purchases, purchaseRevenue
		);
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

	private static LocalDate parseDate(RawRow raw, String column, List<RowValidationError> errors) {
		String value = requireField(raw, column, errors);
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException e) {
			errors.add(new RowValidationError(raw.rowNo(), "INVALID_DATE", "날짜 형식이 올바르지 않습니다 (yyyy-MM-dd): " + value));
			return null;
		}
	}

	private static Long parseNonNegativeLong(RawRow raw, String column, List<RowValidationError> errors) {
		String value = requireField(raw, column, errors);
		if (value == null) {
			return null;
		}
		try {
			long parsed = Long.parseLong(value);
			if (parsed < 0) {
				errors.add(new RowValidationError(raw.rowNo(), "NEGATIVE_VALUE", column + " 값은 음수일 수 없습니다: " + value));
				return null;
			}
			return parsed;
		} catch (NumberFormatException e) {
			errors.add(new RowValidationError(raw.rowNo(), "INVALID_NUMBER", column + " 값이 숫자가 아닙니다: " + value));
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

}
