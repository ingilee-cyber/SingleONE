package com.singleone.backend.analytics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.singleone.backend.domain.common.Media;
import com.singleone.backend.domain.project.ProjectCampaignId;
import com.singleone.backend.domain.upload.UploadBatch;
import com.singleone.backend.domain.upload.UploadBatchRepository;
import com.singleone.backend.domain.upload.UploadStatus;
import com.singleone.backend.domain.upload.UploadType;

/**
 * PRD 8.4 "선택 프로젝트·기간의 포함 캠페인 원본을 매체 단위로 집계"를 위해 ClickHouse
 * performance_fact를 일자×매체 단위로 조회한다. PRD 11.6/11.7/AC-30에 따라 SUCCESS batch만
 * 포함하고, 동일 natural key가 여러 SUCCESS batch에 걸쳐 있으면 {@code argMax}로 최신 batch
 * 값만 사용한다(재업로드 confirm-overwrite 시 이중 집계 방지).
 *
 * 조회 쿼리는 PerformanceFactStore와 동일한 이유로 "?" 대신 값을 직접 문자열로 삽입한다
 * (clickhouse-jdbc 0.9.0의 집계 SELECT 파라미터 바인딩 버그 회피).
 */
@Component
public class PerformanceAggregationRepository {

	private final JdbcTemplate clickHouseJdbcTemplate;
	private final UploadBatchRepository uploadBatchRepository;

	public PerformanceAggregationRepository(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate,
			UploadBatchRepository uploadBatchRepository) {
		this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
		this.uploadBatchRepository = uploadBatchRepository;
	}

	public List<DailyMediaTotal> fetchDailyMediaTotals(String advertiserId, List<ProjectCampaignId> campaigns,
			LocalDate from, LocalDate to) {
		if (campaigns.isEmpty()) {
			return List.of();
		}
		List<Long> successBatchIds = uploadBatchRepository
			.findByAdvertiserIdAndTypeAndStatus(advertiserId, UploadType.PERFORMANCE, UploadStatus.SUCCESS)
			.stream().map(UploadBatch::getUploadBatchId).toList();
		if (successBatchIds.isEmpty()) {
			return List.of();
		}

		String batchIdList = successBatchIds.stream().map(String::valueOf).collect(Collectors.joining(","));
		String campaignTuples = campaigns.stream()
			.map(c -> "('%s','%s')".formatted(c.getMedia().name(), escape(c.getCampaignId())))
			.collect(Collectors.joining(","));

		String sql = """
			SELECT date, media,
				sum(impressions) AS impressions, sum(clicks) AS clicks, sum(cost) AS cost,
				sum(purchases) AS purchases, sum(purchase_revenue) AS purchase_revenue
			FROM (
				SELECT date, media, campaign_id, ad_group_id, ad_id,
					argMax(impressions, upload_batch_id) AS impressions,
					argMax(clicks, upload_batch_id) AS clicks,
					argMax(cost, upload_batch_id) AS cost,
					argMax(purchases, upload_batch_id) AS purchases,
					argMax(purchase_revenue, upload_batch_id) AS purchase_revenue
				FROM performance_fact
				WHERE advertiser_id = '%s' AND date >= '%s' AND date <= '%s'
					AND upload_batch_id IN (%s) AND (media, campaign_id) IN (%s)
				GROUP BY date, media, campaign_id, ad_group_id, ad_id
			)
			GROUP BY date, media
			ORDER BY media, date
			""".formatted(escape(advertiserId), from, to, batchIdList, campaignTuples);

		List<DailyMediaTotal> results = new ArrayList<>();
		clickHouseJdbcTemplate.query(sql, rs -> {
			results.add(new DailyMediaTotal(
				rs.getDate("date").toLocalDate(),
				Media.valueOf(rs.getString("media")),
				rs.getBigDecimal("impressions"),
				rs.getBigDecimal("clicks"),
				rs.getBigDecimal("cost"),
				rs.getBigDecimal("purchases"),
				rs.getBigDecimal("purchase_revenue")));
		});
		return results;
	}

	/**
	 * PRD 7장 상세 화면용: 특정 매체(+선택적으로 캠페인/광고그룹/광고까지) 범위의 기간 전체 합계를
	 * (campaign_id, ad_group_id, ad_id) 단위로 반환한다. Index를 계산하지 않는 레벨이라 날짜별
	 * 운영일 집계는 필요 없어 {@link #fetchDailyMediaTotals}와 달리 기간 전체를 바로 합산한다.
	 * campaignId/adGroupId/adId는 null이면 해당 조건 없이 하위 전체를 포함한다.
	 */
	public List<EntityAggregateRow> fetchEntityTotals(String advertiserId, Media media, String campaignId,
			String adGroupId, String adId, LocalDate from, LocalDate to) {
		List<Long> successBatchIds = uploadBatchRepository
			.findByAdvertiserIdAndTypeAndStatus(advertiserId, UploadType.PERFORMANCE, UploadStatus.SUCCESS)
			.stream().map(UploadBatch::getUploadBatchId).toList();
		if (successBatchIds.isEmpty()) {
			return List.of();
		}
		String batchIdList = successBatchIds.stream().map(String::valueOf).collect(Collectors.joining(","));

		StringBuilder scopeFilter = new StringBuilder();
		if (campaignId != null) {
			scopeFilter.append(" AND campaign_id = '").append(escape(campaignId)).append("'");
		}
		if (adGroupId != null) {
			scopeFilter.append(" AND ad_group_id = '").append(escape(adGroupId)).append("'");
		}
		if (adId != null) {
			scopeFilter.append(" AND ad_id = '").append(escape(adId)).append("'");
		}

		String sql = """
			SELECT campaign_id, ad_group_id, ad_id,
				sum(impressions) AS impressions, sum(clicks) AS clicks, sum(cost) AS cost,
				sum(purchases) AS purchases, sum(purchase_revenue) AS purchase_revenue
			FROM (
				SELECT date, campaign_id, ad_group_id, ad_id,
					argMax(impressions, upload_batch_id) AS impressions,
					argMax(clicks, upload_batch_id) AS clicks,
					argMax(cost, upload_batch_id) AS cost,
					argMax(purchases, upload_batch_id) AS purchases,
					argMax(purchase_revenue, upload_batch_id) AS purchase_revenue
				FROM performance_fact
				WHERE advertiser_id = '%s' AND media = '%s' AND date >= '%s' AND date <= '%s'
					AND upload_batch_id IN (%s)%s
				GROUP BY date, campaign_id, ad_group_id, ad_id
			)
			GROUP BY campaign_id, ad_group_id, ad_id
			""".formatted(escape(advertiserId), media.name(), from, to, batchIdList, scopeFilter);

		List<EntityAggregateRow> results = new ArrayList<>();
		clickHouseJdbcTemplate.query(sql, rs -> {
			results.add(new EntityAggregateRow(
				rs.getString("campaign_id"),
				rs.getString("ad_group_id"),
				rs.getString("ad_id"),
				rs.getBigDecimal("impressions"),
				rs.getBigDecimal("clicks"),
				rs.getBigDecimal("cost"),
				rs.getBigDecimal("purchases"),
				rs.getBigDecimal("purchase_revenue")));
		});
		return results;
	}

	private static String escape(String value) {
		return value.replace("'", "''");
	}

}
