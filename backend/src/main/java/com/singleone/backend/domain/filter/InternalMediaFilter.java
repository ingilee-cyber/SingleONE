package com.singleone.backend.domain.filter;

import java.math.BigDecimal;

import com.singleone.backend.domain.common.Media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * PRD 8.3: 매체별 고정 테스트 필터율. Backend 비공개 내부 설정이며 어떤 Controller/DTO에도
 * 노출하지 않는다 (CLAUDE.md Hard Rule 7). SingleONE 계산은 BigDecimal 기반이어야 하므로
 * filter_rate는 BigDecimal로 매핑한다 (CLAUDE.md Hard Rule 13).
 */
@Entity
@Table(name = "internal_media_filter")
public class InternalMediaFilter {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "media", length = 20)
	private Media media;

	@Column(name = "filter_rate", nullable = false, precision = 6, scale = 4)
	private BigDecimal filterRate;

	protected InternalMediaFilter() {
	}

	public Media getMedia() {
		return media;
	}

	public BigDecimal getFilterRate() {
		return filterRate;
	}

}
