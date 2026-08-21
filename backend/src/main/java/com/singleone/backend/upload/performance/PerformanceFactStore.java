package com.singleone.backend.upload.performance;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PRD 11.2/12.2: ClickHouse performance_fact 테이블 접근. JPA/Hibernate 대상이 아니므로
 * ClickHouse 전용 JdbcTemplate만 사용한다 (CLAUDE.md Hard Rule 11).
 *
 * 조회 쿼리는 "?" PreparedStatement 파라미터 대신 값을 직접 문자열로 넣는다. 이 프로젝트의
 * clickhouse-jdbc 0.9.0에서 집계 함수가 포함된 SELECT에 "?"를 사용하면
 * ArrayIndexOutOfBoundsException이 발생하는 드라이버 버그가 있어(INSERT의 "?" 바인딩은 정상
 * 동작함을 확인함), 여기서 다루는 값(내부 생성 Long ID, 검증된 advertiser_id, LocalDate)에
 * 한해 안전하게 이스케이프해 직접 삽입하는 방식으로 우회한다.
 */
@Component
public class PerformanceFactStore {

	private static final String INSERT_SQL = """
		INSERT INTO performance_fact
			(date, advertiser_id, advertiser_name, media, campaign_id, campaign_name,
			 ad_group_id, ad_group_name, ad_id, ad_name,
			 impressions, clicks, cost, add_to_cart, purchases, purchase_revenue, upload_batch_id)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		""";

	private final JdbcTemplate clickHouseJdbcTemplate;

	public PerformanceFactStore(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate) {
		this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
	}

	public void insertBatch(List<PerformanceRow> rows, long uploadBatchId) {
		if (rows.isEmpty()) {
			return;
		}
		clickHouseJdbcTemplate.batchUpdate(INSERT_SQL, rows, rows.size(), (ps, row) -> {
			ps.setObject(1, row.date());
			ps.setString(2, row.advertiserId());
			ps.setString(3, row.advertiserName());
			ps.setString(4, row.media().name());
			ps.setString(5, row.campaignId());
			ps.setString(6, row.campaignName());
			ps.setString(7, row.adGroupId());
			ps.setString(8, row.adGroupName());
			ps.setString(9, row.adId());
			ps.setString(10, row.adName());
			ps.setLong(11, row.impressions());
			ps.setLong(12, row.clicks());
			ps.setBigDecimal(13, row.cost());
			ps.setLong(14, row.addToCart());
			ps.setLong(15, row.purchases());
			ps.setBigDecimal(16, row.purchaseRevenue());
			ps.setLong(17, uploadBatchId);
		});
	}

	public void deleteByBatch(long uploadBatchId) {
		clickHouseJdbcTemplate.execute("ALTER TABLE performance_fact DELETE WHERE upload_batch_id = " + uploadBatchId);
	}

	/**
	 * PRD 11.6: 기존 SUCCESS 데이터와 natural key(PRD 11.3)가 중복되는지 확인한다.
	 */
	public Set<String> findExistingSuccessNaturalKeys(String advertiserId, List<Long> successBatchIds,
			LocalDate minDate, LocalDate maxDate) {
		if (successBatchIds.isEmpty()) {
			return new HashSet<>();
		}
		String inClause = successBatchIds.stream().map(String::valueOf).collect(Collectors.joining(","));
		String sql = """
			SELECT date, media, campaign_id, ad_group_id, ad_id
			FROM performance_fact
			WHERE advertiser_id = '%s' AND date >= '%s' AND date <= '%s' AND upload_batch_id IN (%s)
			""".formatted(escape(advertiserId), minDate, maxDate, inClause);

		Set<String> keys = new HashSet<>();
		clickHouseJdbcTemplate.query(sql, rs -> {
			// PerformanceRow.naturalKey()와 반드시 같은 순서/구성이어야 한다 (PRD 11.3).
			keys.add(String.join("|",
				rs.getDate("date").toLocalDate().toString(),
				advertiserId,
				rs.getString("media"),
				rs.getString("campaign_id"),
				rs.getString("ad_group_id"),
				rs.getString("ad_id")));
		});
		return keys;
	}

	public record LatestName(String key1, String key2, String name, LocalDate date) {
	}

	public LatestName latestAdvertiserName(long uploadBatchId) {
		String sql = """
			SELECT argMax(advertiser_name, date) AS name, max(date) AS latest_date
			FROM performance_fact WHERE upload_batch_id = %d
			""".formatted(uploadBatchId);
		return clickHouseJdbcTemplate.query(sql,
			rs -> rs.next() ? new LatestName(null, null, rs.getString("name"), rs.getDate("latest_date").toLocalDate()) : null);
	}

	public List<CampaignCandidate> latestCampaignNames(long uploadBatchId) {
		String sql = """
			SELECT media, campaign_id, argMax(campaign_name, date) AS name, max(date) AS latest_date
			FROM performance_fact WHERE upload_batch_id = %d GROUP BY media, campaign_id
			""".formatted(uploadBatchId);
		return clickHouseJdbcTemplate.query(sql, (rs, rowNum) -> new CampaignCandidate(
			rs.getString("media"), rs.getString("campaign_id"), rs.getString("name"), rs.getDate("latest_date").toLocalDate()));
	}

	public record CampaignCandidate(String media, String campaignId, String name, LocalDate date) {
	}

	public List<AdGroupCandidate> latestAdGroupNames(long uploadBatchId) {
		String sql = """
			SELECT media, campaign_id, ad_group_id, argMax(ad_group_name, date) AS name, max(date) AS latest_date
			FROM performance_fact WHERE upload_batch_id = %d GROUP BY media, campaign_id, ad_group_id
			""".formatted(uploadBatchId);
		return clickHouseJdbcTemplate.query(sql, (rs, rowNum) -> new AdGroupCandidate(
			rs.getString("media"), rs.getString("campaign_id"), rs.getString("ad_group_id"),
			rs.getString("name"), rs.getDate("latest_date").toLocalDate()));
	}

	public record AdGroupCandidate(String media, String campaignId, String adGroupId, String name, LocalDate date) {
	}

	public List<AdCandidate> latestAdNames(long uploadBatchId) {
		String sql = """
			SELECT media, campaign_id, ad_group_id, ad_id, argMax(ad_name, date) AS name, max(date) AS latest_date
			FROM performance_fact WHERE upload_batch_id = %d GROUP BY media, campaign_id, ad_group_id, ad_id
			""".formatted(uploadBatchId);
		return clickHouseJdbcTemplate.query(sql, (rs, rowNum) -> new AdCandidate(
			rs.getString("media"), rs.getString("campaign_id"), rs.getString("ad_group_id"), rs.getString("ad_id"),
			rs.getString("name"), rs.getDate("latest_date").toLocalDate()));
	}

	public record AdCandidate(String media, String campaignId, String adGroupId, String adId, String name, LocalDate date) {
	}

	private static String escape(String value) {
		return value.replace("'", "''");
	}

}
