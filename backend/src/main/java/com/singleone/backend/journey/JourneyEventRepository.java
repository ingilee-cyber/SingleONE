package com.singleone.backend.journey;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.singleone.backend.domain.common.JourneyEventType;
import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.upload.UploadBatch;
import com.singleone.backend.domain.upload.UploadBatchRepository;
import com.singleone.backend.domain.upload.UploadStatus;
import com.singleone.backend.domain.upload.UploadType;

/**
 * PRD 9장 Journey 분석용 ClickHouse journey_event 조회. PRD 11.6/AC-30에 따라 SUCCESS batch만
 * 포함하고, PRD 11.4의 natural key(advertiser_id + event_id) 기준으로 {@code argMax}를 이용해
 * event_id당 최신 batch 값만 사용한다({@link com.singleone.backend.analytics.PerformanceAggregationRepository}와
 * 동일한 dedup 철학, event_id가 반복 불가능한 키라 GROUP BY event_id 하나로 충분하다는 점만 다르다).
 *
 * 조회 쿼리는 같은 이유(clickhouse-jdbc 0.9.0의 집계 SELECT "?" 바인딩 버그)로 값을 직접 문자열로
 * 삽입한다. 날짜 필터는 {@code java.sql.Timestamp}가 아니라 {@link DateTimeFormatter}로 UTC
 * "yyyy-MM-dd HH:mm:ss" 문자열을 직접 만든다({@code Timestamp.toString()}은 초 단위가 0이어도
 * ".0"을 붙여 ClickHouse DateTime 파싱이 실패한다).
 */
@Component
public class JourneyEventRepository {

	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
		.withZone(ZoneOffset.UTC);

	private final JdbcTemplate clickHouseJdbcTemplate;
	private final UploadBatchRepository uploadBatchRepository;

	public JourneyEventRepository(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate,
			UploadBatchRepository uploadBatchRepository) {
		this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
		this.uploadBatchRepository = uploadBatchRepository;
	}

	public List<JourneyEventRecord> fetchEvents(String advertiserId, Instant fromInclusive, Instant toExclusive) {
		List<Long> successBatchIds = uploadBatchRepository
			.findByAdvertiserIdAndTypeAndStatus(advertiserId, UploadType.JOURNEY, UploadStatus.SUCCESS)
			.stream().map(UploadBatch::getUploadBatchId).toList();
		if (successBatchIds.isEmpty()) {
			return List.of();
		}
		String batchIdList = successBatchIds.stream().map(String::valueOf).collect(Collectors.joining(","));

		// ClickHouse는 WHERE절에서 SELECT의 집계 별칭(예: argMax(...) AS event_timestamp)을
		// 참조하면 ILLEGAL_AGGREGATION 오류를 낸다. fetchEntityTotals와 동일하게 내부 쿼리에서
		// 원본 컬럼으로 먼저 필터링한 뒤, 바깥 쿼리에서 event_id 기준으로 argMax dedup한다.
		String sql = """
			SELECT event_id,
				argMax(anonymous_user_id, upload_batch_id) AS anonymous_user_id,
				argMax(event_timestamp, upload_batch_id) AS event_timestamp,
				argMax(event_type, upload_batch_id) AS event_type,
				argMax(media, upload_batch_id) AS media,
				argMax(campaign_id, upload_batch_id) AS campaign_id,
				argMax(order_id, upload_batch_id) AS order_id,
				argMax(purchase_revenue, upload_batch_id) AS purchase_revenue
			FROM (
				SELECT event_id, anonymous_user_id, event_timestamp, event_type, media, campaign_id,
					order_id, purchase_revenue, upload_batch_id
				FROM journey_event
				WHERE advertiser_id = '%s' AND event_timestamp >= '%s' AND event_timestamp < '%s'
					AND upload_batch_id IN (%s)
			)
			GROUP BY event_id
			""".formatted(escape(advertiserId), DATE_TIME_FORMAT.format(fromInclusive), DATE_TIME_FORMAT.format(toExclusive),
			batchIdList);

		List<JourneyEventRecord> results = new ArrayList<>();
		clickHouseJdbcTemplate.query(sql, rs -> {
			String mediaValue = rs.getString("media");
			// getBigDecimal은 SQL NULL이면 (다른 getXXX 호출과 무관하게) 그대로 null을 반환한다.
			BigDecimal purchaseRevenue = rs.getBigDecimal("purchase_revenue");
			results.add(new JourneyEventRecord(
				rs.getString("event_id"),
				rs.getString("anonymous_user_id"),
				rs.getTimestamp("event_timestamp").toInstant(),
				JourneyEventType.valueOf(rs.getString("event_type")),
				mediaValue == null ? null : Media.valueOf(mediaValue),
				rs.getString("campaign_id"),
				rs.getString("order_id"),
				purchaseRevenue));
		});
		return results;
	}

	private static String escape(String value) {
		return value.replace("'", "''");
	}

}
