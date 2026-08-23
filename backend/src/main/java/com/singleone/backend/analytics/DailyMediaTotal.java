package com.singleone.backend.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.singleone.backend.domain.common.Media;

/**
 * 일자×매체 단위 원본 성과(자연키별 중복 제거 후 합산됨, PRD 11.6). 기간 합산과
 * 7일 Rolling window(PRD 8.9) 계산에 공통으로 재사용되는 최소 단위다.
 */
public record DailyMediaTotal(
	LocalDate date,
	Media media,
	BigDecimal impressions,
	BigDecimal clicks,
	BigDecimal cost,
	BigDecimal rawPurchases,
	BigDecimal rawRevenue
) {
}
