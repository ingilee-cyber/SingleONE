package com.singleone.backend.analytics;

import java.time.LocalDate;
import java.util.List;

/**
 * PRD 8.9 7일 Rolling Index의 날짜 D 시점 결과(window: D-6 ~ D).
 * 해당 window의 유효 비교 매체가 1개 이하인 날짜는 결과 목록에서 아예 제외한다(PRD 8.9/AC-16).
 */
public record RollingIndexPoint(LocalDate date, List<MediaIndexResult> mediaResults) {
}
