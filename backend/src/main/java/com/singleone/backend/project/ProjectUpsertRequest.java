package com.singleone.backend.project;

import java.util.List;

/** 프로젝트 생성/수정 공용 요청. */
public record ProjectUpsertRequest(String projectName, List<CampaignSelection> campaigns) {
}
