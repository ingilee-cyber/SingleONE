package com.singleone.backend.upload.journey;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PRD 9.5/12.2: ClickHouse journey_event 테이블 접근.
 * 조회 쿼리에서 "?" 대신 값을 직접 삽입하는 이유는 {@link com.singleone.backend.upload.performance.PerformanceFactStore}의 클래스 주석 참고.
 */
@Component
public class JourneyEventStore {

	private static final String INSERT_SQL = """
		INSERT INTO journey_event
			(event_id, advertiser_id, anonymous_user_id, event_timestamp, event_type,
			 media, campaign_id, ad_group_id, ad_id, order_id, purchase_revenue, upload_batch_id)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		""";

	private final JdbcTemplate clickHouseJdbcTemplate;

	public JourneyEventStore(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate) {
		this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
	}

	public void insertBatch(List<JourneyRow> rows, long uploadBatchId) {
		if (rows.isEmpty()) {
			return;
		}
		clickHouseJdbcTemplate.batchUpdate(INSERT_SQL, rows, rows.size(), (ps, row) -> {
			ps.setString(1, row.eventId());
			ps.setString(2, row.advertiserId());
			ps.setString(3, row.anonymousUserId());
			ps.setTimestamp(4, Timestamp.from(row.eventTimestamp()));
			ps.setString(5, row.eventType().name());
			ps.setString(6, row.media() == null ? null : row.media().name());
			ps.setString(7, row.campaignId());
			ps.setString(8, row.adGroupId());
			ps.setString(9, row.adId());
			ps.setString(10, row.orderId());
			ps.setBigDecimal(11, row.purchaseRevenue());
			ps.setLong(12, uploadBatchId);
		});
	}

	public void deleteByBatch(long uploadBatchId) {
		clickHouseJdbcTemplate.execute("ALTER TABLE journey_event DELETE WHERE upload_batch_id = " + uploadBatchId);
	}

	/**
	 * PRD 11.4: Event unique key = advertiser_id + event_id. 기존 SUCCESS 데이터와 중복 확인(PRD 11.6)에 사용한다.
	 */
	public Set<String> findExistingSuccessEventKeys(String advertiserId, List<Long> successBatchIds,
			Instant fromInclusive, Instant toInclusive) {
		if (successBatchIds.isEmpty()) {
			return new HashSet<>();
		}
		String inClause = successBatchIds.stream().map(String::valueOf).collect(Collectors.joining(","));
		String sql = """
			SELECT event_id
			FROM journey_event
			WHERE advertiser_id = '%s' AND event_timestamp >= '%s' AND event_timestamp <= '%s' AND upload_batch_id IN (%s)
			""".formatted(escape(advertiserId), Timestamp.from(fromInclusive), Timestamp.from(toInclusive), inClause);

		Set<String> keys = new HashSet<>();
		clickHouseJdbcTemplate.query(sql, rs -> {
			keys.add(advertiserId + "|" + rs.getString("event_id"));
		});
		return keys;
	}

	private static String escape(String value) {
		return value.replace("'", "''");
	}

}
