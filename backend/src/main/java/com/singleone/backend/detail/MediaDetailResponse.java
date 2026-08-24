package com.singleone.backend.detail;

import java.util.List;

import com.singleone.backend.analytics.MediaIndexResult;
import com.singleone.backend.analytics.RollingIndexPoint;

/**
 * PRD 7.2 매체 상세 응답. Dashboard(Stage 5)와 동일한 계산 결과에서 해당 매체 1개만 추린 것이라
 * 별도 계산이 없다.
 */
public record MediaDetailResponse(MediaIndexResult current, MediaIndexResult previous, List<RollingIndexPoint> rolling) {
}
