package com.singleone.backend.detail;

/** PRD 7.3: 캠페인 상세의 이전 기간 비교(광고그룹/광고 상세에는 없음). */
public record EntityPerformanceComparison(EntityPerformance current, EntityPerformance previous) {
}
