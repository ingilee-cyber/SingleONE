package com.singleone.backend.analytics;

/**
 * PRD 8.6 Index 산출 결과 상태.
 */
public enum IndexStatus {
	/** 최소 조건을 만족하고 유효 비교 매체가 2개 이상이라 점수가 산출됨. */
	VALID,
	/** 원본 데이터는 있으나 운영일/Cost/SingleONE purchases 최소 조건 중 하나 이상 미달. */
	INSUFFICIENT_DATA,
	/** 프로젝트에 포함된 매체인데 선택 기간에 성과 원본 데이터가 전혀 없음. */
	MISSING_REQUIRED_DATA,
	/** 최소 조건을 만족하는 매체가 1개 이하라 상대 비교 자체가 불가능함. */
	COMPARISON_MEDIA_INSUFFICIENT
}
