package com.singleone.backend.project;

import com.singleone.backend.domain.common.Media;

/** 프로젝트 생성/수정 요청에 담기는 캠페인 선택 항목(PRD 5.4 복합키의 media+campaign_id 부분). */
public record CampaignSelection(Media media, String campaignId) {
}
