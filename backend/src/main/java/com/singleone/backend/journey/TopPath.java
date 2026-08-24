package com.singleone.backend.journey;

import java.math.BigDecimal;
import java.util.List;

import com.singleone.backend.domain.common.Media;

/**
 * PRD 9.1/AC-55: 연속 동일 채널을 압축한 경로 하나와 그 경로를 따른 구매수/구매매출.
 * Sankey와 Top Path 테이블이 동일하게 이 목록(Top 20)을 사용한다.
 */
public record TopPath(List<Media> channels, long purchaseCount, BigDecimal purchaseRevenue) {
}
