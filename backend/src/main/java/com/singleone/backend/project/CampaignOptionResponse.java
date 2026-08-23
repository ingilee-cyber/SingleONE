package com.singleone.backend.project;

import com.singleone.backend.domain.common.Media;

/** 캠페인 선택 UI(PRD 5.3)와 프로젝트 응답에 포함된 캠페인 항목에 공통으로 사용한다. */
public record CampaignOptionResponse(Media media, String campaignId, String campaignName) {
}
