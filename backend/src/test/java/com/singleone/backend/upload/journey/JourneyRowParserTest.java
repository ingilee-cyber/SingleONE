package com.singleone.backend.upload.journey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.singleone.backend.domain.common.JourneyEventType;
import com.singleone.backend.upload.parse.RawRow;

class JourneyRowParserTest {

	private static final String ADVERTISER_ID = "adv-1";

	private RawRow clickRow() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("event_id", "evt-1");
		values.put("anonymous_user_id", "user-1");
		values.put("event_timestamp", "2026-08-12T00:00:00Z");
		values.put("advertiser_id", ADVERTISER_ID);
		values.put("event_type", "CLICK");
		values.put("media", "META");
		values.put("campaign_id", "camp-1");
		return new RawRow(1, values);
	}

	private RawRow purchaseRow() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("event_id", "evt-2");
		values.put("anonymous_user_id", "user-1");
		values.put("event_timestamp", "2026-08-12T01:00:00Z");
		values.put("advertiser_id", ADVERTISER_ID);
		values.put("event_type", "PURCHASE");
		values.put("order_id", "order-1");
		values.put("purchase_revenue", "100000");
		return new RawRow(2, values);
	}

	@Test
	void parsesValidClickRow() {
		JourneyRowParser.Result result = JourneyRowParser.parse(clickRow(), ADVERTISER_ID);

		assertThat(result.isValid()).isTrue();
		assertThat(result.row().eventType()).isEqualTo(JourneyEventType.CLICK);
		assertThat(result.row().eventNaturalKey()).isEqualTo("adv-1|evt-1");
	}

	@Test
	void parsesValidPurchaseRow() {
		JourneyRowParser.Result result = JourneyRowParser.parse(purchaseRow(), ADVERTISER_ID);

		assertThat(result.isValid()).isTrue();
		assertThat(result.row().eventType()).isEqualTo(JourneyEventType.PURCHASE);
		assertThat(result.row().orderId()).isEqualTo("order-1");
		assertThat(result.row().media()).isNull();
	}

	@Test
	void clickRequiresMediaAndCampaign() {
		RawRow raw = clickRow();
		raw.values().remove("media");
		raw.values().remove("campaign_id");

		JourneyRowParser.Result result = JourneyRowParser.parse(raw, ADVERTISER_ID);

		assertThat(result.isValid()).isFalse();
		assertThat(result.errors()).hasSize(2);
	}

	@Test
	void purchaseRequiresOrderIdAndRevenue() {
		RawRow raw = purchaseRow();
		raw.values().remove("order_id");
		raw.values().remove("purchase_revenue");

		JourneyRowParser.Result result = JourneyRowParser.parse(raw, ADVERTISER_ID);

		assertThat(result.isValid()).isFalse();
		assertThat(result.errors()).hasSize(2);
	}

	@Test
	void rejectsInvalidEventType() {
		RawRow raw = clickRow();
		raw.values().put("event_type", "VIEW");

		JourneyRowParser.Result result = JourneyRowParser.parse(raw, ADVERTISER_ID);

		assertThat(result.isValid()).isFalse();
		assertThat(result.errors()).anySatisfy(e -> assertThat(e.errorCode()).isEqualTo("INVALID_EVENT_TYPE"));
	}

}
