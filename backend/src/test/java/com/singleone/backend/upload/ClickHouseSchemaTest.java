package com.singleone.backend.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import com.singleone.backend.AbstractIntegrationTest;
import com.singleone.backend.domain.common.JourneyEventType;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.upload.journey.JourneyEventStore;
import com.singleone.backend.upload.journey.JourneyRow;

/**
 * PRD 11.2/9.5/12.2 ClickHouse 스키마를 검증한다.
 */
class ClickHouseSchemaTest extends AbstractIntegrationTest {

	@Autowired
	@Qualifier("clickHouseJdbcTemplate")
	private JdbcTemplate clickHouseJdbcTemplate;

	@Autowired
	private JourneyEventStore journeyEventStore;

	@Test
	void performanceFactHasExpectedColumns() {
		assertThat(columnsOf("performance_fact")).containsExactlyInAnyOrder(
			"date", "advertiser_id", "advertiser_name", "media", "campaign_id", "campaign_name",
			"ad_group_id", "ad_group_name", "ad_id", "ad_name",
			"impressions", "clicks", "cost", "add_to_cart", "purchases", "purchase_revenue", "upload_batch_id");
	}

	@Test
	void journeyEventHasExpectedColumns() {
		assertThat(columnsOf("journey_event")).containsExactlyInAnyOrder(
			"event_id", "advertiser_id", "anonymous_user_id", "event_timestamp", "event_type",
			"media", "campaign_id", "ad_group_id", "ad_id", "order_id", "purchase_revenue", "upload_batch_id");
	}

	@Test
	void journeyEventStoresClickAndPurchaseWithCorrectNullableFields() {
		JourneyRow click = new JourneyRow("evt-click", "adv-ch-1", "user-1", Instant.parse("2026-08-12T00:00:00Z"),
			JourneyEventType.CLICK, Media.META, "camp-1", "ag-1", "ad-1", null, null);
		JourneyRow purchase = new JourneyRow("evt-purchase", "adv-ch-1", "user-1", Instant.parse("2026-08-12T01:00:00Z"),
			JourneyEventType.PURCHASE, null, null, null, null, "order-1", new BigDecimal("50000"));

		journeyEventStore.insertBatch(List.of(click, purchase), 999L);

		clickHouseJdbcTemplate.query(
			"SELECT event_type, media, order_id, purchase_revenue FROM journey_event WHERE event_id = 'evt-click'",
			rs -> {
				assertThat(rs.next()).isTrue();
				assertThat(rs.getString("event_type")).isEqualTo("CLICK");
				assertThat(rs.getString("media")).isEqualTo("META");
				assertThat(rs.getString("order_id")).isNull();
			});

		clickHouseJdbcTemplate.query(
			"SELECT event_type, media, order_id, purchase_revenue FROM journey_event WHERE event_id = 'evt-purchase'",
			rs -> {
				assertThat(rs.next()).isTrue();
				assertThat(rs.getString("event_type")).isEqualTo("PURCHASE");
				assertThat(rs.getString("media")).isNull();
				assertThat(rs.getBigDecimal("purchase_revenue")).isEqualByComparingTo("50000");
			});
	}

	private List<String> columnsOf(String table) {
		return clickHouseJdbcTemplate.queryForList(
			"SELECT name FROM system.columns WHERE database = currentDatabase() AND table = '" + table + "'",
			String.class);
	}

}
