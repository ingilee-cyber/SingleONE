import { PROJECT_TYPE_CODE } from "./constants.mjs";

const MEDIA_LABEL_KO = {
  META: "메타",
  TIKTOK: "틱톡",
  GOOGLE: "구글",
  NAVER: "네이버",
  CRITEO: "크리테오",
};

/**
 * 광고주/매체/프로젝트유형별 캠페인→광고그룹→광고 계층을 만든다. ID는 사람이 읽을 수 있게
 * `{광고주코드}-{매체}-{프로젝트코드}-C{NN}` 형태로 짓는다.
 * 실제 unique 제약(코드 조사 결과): campaign_id는 (advertiser,media) 내에서만, ad_group_id는
 * (advertiser,media,campaign) 내에서만, ad_id는 그 하위에서만 unique면 되므로 이 규칙과 충돌 없음.
 *
 * @param {string} advCode 광고주 접두어(예: AUR)
 * @param {string} advName 광고주 한글명(캠페인명 표시용)
 * @param {string} media
 * @param {string} projectType ALWAYS|PROMO_JULY|PROMO_AUG
 * @param {number} campaignCount 이 매체에 만들 캠페인 수(기본 2, 일부는 페이지네이션 테스트용으로 늘림)
 * @param {number} adGroupsPerCampaign
 * @param {number} adsPerAdGroup
 */
export function buildHierarchy(advCode, advName, media, projectType, {
  campaignCount = 2,
  adGroupsPerCampaign = 2,
  adsPerAdGroup = 3,
} = {}) {
  const projCode = PROJECT_TYPE_CODE[projectType];
  const mediaLabel = MEDIA_LABEL_KO[media];
  const campaigns = [];

  for (let c = 1; c <= campaignCount; c++) {
    const campaignId = `${advCode}-${media}-${projCode}-C${String(c).padStart(2, "0")}`;
    const campaignName = `${advName} ${mediaLabel} ${projCodeLabel(projectType)} 캠페인 ${c}`;
    const adGroups = [];
    for (let g = 1; g <= adGroupsPerCampaign; g++) {
      const adGroupId = `${campaignId}-G${String(g).padStart(2, "0")}`;
      const adGroupName = `${campaignName} 광고그룹 ${g}`;
      const ads = [];
      for (let a = 1; a <= adsPerAdGroup; a++) {
        const adId = `${adGroupId}-A${String(a).padStart(2, "0")}`;
        const adName = `${adGroupName} 광고 ${a}`;
        ads.push({ adId, adName });
      }
      adGroups.push({ adGroupId, adGroupName, ads });
    }
    campaigns.push({ campaignId, campaignName, adGroups });
  }
  return campaigns;
}

function projCodeLabel(projectType) {
  return projectType === "ALWAYS" ? "상시" : projectType === "PROMO_JULY" ? "7월프로모션" : "8월프로모션";
}
