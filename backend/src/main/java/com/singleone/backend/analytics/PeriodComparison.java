package com.singleone.backend.analytics;

import java.util.List;

/**
 * PRD 8.8 이전 기간 비교 결과. previous의 매체별 status가 VALID가 아니면
 * "이전 기간 데이터 부족" 등으로 해석한다.
 */
public record PeriodComparison(List<MediaIndexResult> current, List<MediaIndexResult> previous) {
}
