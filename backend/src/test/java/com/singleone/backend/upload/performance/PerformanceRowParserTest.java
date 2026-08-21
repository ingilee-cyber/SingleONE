package com.singleone.backend.upload.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.singleone.backend.domain.common.Media;
import com.singleone.backend.upload.parse.RawRow;

class PerformanceRowParserTest {

	private static final String ADVERTISER_ID = "adv-1";

	private RawRow validRawRow() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("date", "2026-08-12");
		values.put("advertiser_id", ADVERTISER_ID);
		values.put("advertiser_name", "ABC Brand");
		values.put("media", "META");
		values.put("campaign_id", "camp-1");
		values.put("campaign_name", "여름 프로모션");
		values.put("ad_group_id", "ag-1");
		values.put("ad_group_name", "그룹1");
		values.put("ad_id", "ad-1");
		values.put("ad_name", "광고1");
		values.put("impressions", "1000");
		values.put("clicks", "50");
		values.put("cost", "100000.5");
		values.put("add_to_cart", "10");
		values.put("purchases", "5");
		values.put("purchase_revenue", "500000.25");
		return new RawRow(1, values);
	}

	@Test
	void parsesValidRow() {
		PerformanceRowParser.Result result = PerformanceRowParser.parse(validRawRow(), ADVERTISER_ID);

		assertThat(result.isValid()).isTrue();
		assertThat(result.row().media()).isEqualTo(Media.META);
		assertThat(result.row().naturalKey()).isEqualTo("2026-08-12|adv-1|META|camp-1|ag-1|ad-1");
	}

	@Test
	void rejectsMissingRequiredField() {
		RawRow raw = validRawRow();
		raw.values().put("campaign_id", "");

		PerformanceRowParser.Result result = PerformanceRowParser.parse(raw, ADVERTISER_ID);

		assertThat(result.isValid()).isFalse();
		assertThat(result.errors()).anySatisfy(e -> assertThat(e.errorCode()).isEqualTo("REQUIRED_FIELD_MISSING"));
	}

	@Test
	void rejectsNegativeCost() {
		RawRow raw = validRawRow();
		raw.values().put("cost", "-1");

		PerformanceRowParser.Result result = PerformanceRowParser.parse(raw, ADVERTISER_ID);

		assertThat(result.isValid()).isFalse();
		assertThat(result.errors()).anySatisfy(e -> assertThat(e.errorCode()).isEqualTo("NEGATIVE_VALUE"));
	}

	@Test
	void rejectsUnsupportedMedia() {
		RawRow raw = validRawRow();
		raw.values().put("media", "FACEBOOK_ADS");

		PerformanceRowParser.Result result = PerformanceRowParser.parse(raw, ADVERTISER_ID);

		assertThat(result.isValid()).isFalse();
		assertThat(result.errors()).anySatisfy(e -> assertThat(e.errorCode()).isEqualTo("UNSUPPORTED_MEDIA"));
	}

	@Test
	void rejectsAdvertiserMismatch() {
		PerformanceRowParser.Result result = PerformanceRowParser.parse(validRawRow(), "other-advertiser");

		assertThat(result.isValid()).isFalse();
		assertThat(result.errors()).anySatisfy(e -> assertThat(e.errorCode()).isEqualTo("ADVERTISER_MISMATCH"));
	}

	@Test
	void rejectsInvalidDate() {
		RawRow raw = validRawRow();
		raw.values().put("date", "2026/08/12");

		PerformanceRowParser.Result result = PerformanceRowParser.parse(raw, ADVERTISER_ID);

		assertThat(result.isValid()).isFalse();
		assertThat(result.errors()).anySatisfy(e -> assertThat(e.errorCode()).isEqualTo("INVALID_DATE"));
	}

}
