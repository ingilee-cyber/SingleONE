// SingleONE 종합 테스트 데이터 세트 생성 메인 스크립트. `npm run generate`로 실행한다.
// 산출물: test-data/demo-full/{performance,journey,projects,xlsx,invalid-upload}/*
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import writeXlsxFile from "write-excel-file/node";

import {
  SEED, START_DATE, END_DATE, MEDIA_LIST, PROJECT_TYPES, PROJECT_TYPE_LABEL, PROJECT_TYPE_RANGE,
  PROJECT_TYPE_CODE, INTERNAL_FILTER_RATE,
} from "./lib/constants.mjs";
import { ADVERTISERS, mediaPlanFor } from "./lib/brands.mjs";
import { buildHierarchy } from "./lib/hierarchy.mjs";
import { dateRange, lastNWeeksEndingAt, addDays, parseISODate, toISODate } from "./lib/dates.mjs";
import { rngFor, distributeIntegerBySum } from "./lib/rng.mjs";
import {
  generateOrganicDays, generateFlatWeeklyDays, generateSparseWeeklyDays, generateLogCurveWeeklyDays,
  generateActiveOnlyDays, generateZeroPurchaseDays, generateLowPurchaseDays,
} from "./lib/performanceModel.mjs";
import { generateJourneyEvents } from "./lib/journeyModel.mjs";
import { PERFORMANCE_HEADER, JOURNEY_HEADER, rowsToCsv } from "./lib/csv.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..", "demo-full");

const NUM_FIELDS = ["impressions", "clicks", "cost", "addToCart", "purchases", "revenue"];

function campaignWeights(n, primaryShare = 0.6) {
  if (n === 1) return [1];
  const rest = (1 - primaryShare) / (n - 1);
  return [primaryShare, ...Array(n - 1).fill(rest)];
}

/** 캠페인 목록을 (campaign*group*ad) 가중치가 붙은 리프 배열로 펼친다. */
function flattenLeaves(campaigns) {
  const leaves = [];
  const cWeights = campaignWeights(campaigns.length);
  campaigns.forEach((camp, ci) => {
    const gWeights = campaignWeights(camp.adGroups.length, 0.55);
    camp.adGroups.forEach((group, gi) => {
      const aWeights = campaignWeights(group.ads.length, 0.45);
      group.ads.forEach((ad, ai) => {
        leaves.push({
          campaignId: camp.campaignId, campaignName: camp.campaignName,
          adGroupId: group.adGroupId, adGroupName: group.adGroupName,
          adId: ad.adId, adName: ad.adName,
          weight: cWeights[ci] * gWeights[gi] * aWeights[ai],
        });
      });
    });
  });
  return leaves;
}

/** 하루치 매체 합계를 리프(광고) 단위로 자연스럽게 쪼갠다(합계는 정확히 보존). */
function splitDayAcrossLeaves(dayRow, leaves) {
  const weights = leaves.map((l) => l.weight);
  const perField = {};
  for (const field of NUM_FIELDS) {
    perField[field] = distributeIntegerBySum(weights, dayRow[field] ?? 0);
  }
  return leaves.map((leaf, i) => ({
    leaf,
    impressions: perField.impressions[i],
    clicks: perField.clicks[i],
    cost: perField.cost[i],
    addToCart: perField.addToCart[i],
    purchases: perField.purchases[i],
    revenue: perField.revenue[i],
  }));
}

function generateMediaDayRows(advertiserId, projectType, media, plan, dateRangeForType) {
  const key = `${advertiserId}-${projectType}-${media}`;
  const [start, end] = PROJECT_TYPE_RANGE[projectType];
  switch (plan.scenario) {
    case "ORGANIC":
      return generateOrganicDays(SEED, key, dateRangeForType, plan);
    case "LOW_PURCHASE":
      return generateLowPurchaseDays(SEED, key, dateRangeForType, plan, plan.targetTotalPurchases);
    case "ZERO_PURCHASE":
      return generateZeroPurchaseDays(SEED, key, dateRangeForType, plan);
    case "ACTIVE_ONLY": {
      const activeDates = plan.activeDates.filter((d) => dateRangeForType.includes(d));
      return generateActiveOnlyDays(SEED, key, activeDates, plan);
    }
    case "FLAT": {
      // Simulation 후보 8주(backend의 baseTo 역산 방식)와 정확히 같은 경계로 나눠야
      // "주간 cost 변동폭 <1.2배" 조건이 실제로 재현된다(달력 기준 chunkIntoWeeks는 경계가
      // 어긋나 오탐이 생길 수 있음 — 검증 중 발견).
      const last8 = lastNWeeksEndingAt(END_DATE, 8);
      const windowStart = last8[0][0];
      const preWindowDates = dateRange(start, toISODate(addDays(parseISODate(windowStart), -1)));
      const preRows = preWindowDates.length
        ? generateOrganicDays(SEED, `${key}-pre`, preWindowDates, {
            cpm: plan.cpm, ctr: plan.ctr, atcRate: plan.atcRate,
            cvr: plan.cvr, aov: plan.aov, dailyBase: plan.weeklyCostBase / 7,
            noiseRatio: 0.03,
          })
        : [];
      const flatRows = generateFlatWeeklyDays(SEED, `${key}-flat`, last8, plan);
      return [...preRows, ...flatRows];
    }
    case "SPARSE": {
      // 최근 8주(Simulation 후보 window)만 SPARSE로 만들고, 그 이전 기간은 organic 기준값으로 채운다.
      const last8 = lastNWeeksEndingAt(END_DATE, 8);
      const windowStart = last8[0][0];
      const preWindowDates = dateRange(start, toISODate(addDays(parseISODate(windowStart), -1)));
      const preRows = preWindowDates.length
        ? generateOrganicDays(SEED, `${key}-pre`, preWindowDates, {
            cpm: plan.cpm, ctr: plan.ctr, atcRate: plan.atcRate,
            cvr: plan.organicCvr, aov: plan.organicAov, dailyBase: plan.organicDailyBase,
          })
        : [];
      // SPARSE 유효 주차(cost>0)에도 dailyFunnelFromCost가 cvr/aov를 참조하므로, organic* 값을
      // 폴백으로 채워 준다(SPARSE plan은 organicCvr/organicAov만 정의하고 있었음 — 검증 중 발견한
      // NaN purchases/revenue 버그의 원인).
      const sparseParams = { ...plan, cvr: plan.cvr ?? plan.organicCvr, aov: plan.aov ?? plan.organicAov };
      const sparseRows = generateSparseWeeklyDays(SEED, `${key}-sparse`, last8, sparseParams, plan.zeroWeekCount);
      return [...preRows, ...sparseRows];
    }
    case "LOG_CURVE": {
      const last8 = lastNWeeksEndingAt(END_DATE, 8);
      const windowStart = last8[0][0];
      const preWindowDates = dateRange(start, toISODate(addDays(parseISODate(windowStart), -1)));
      const preRows = preWindowDates.length
        ? generateOrganicDays(SEED, `${key}-pre`, preWindowDates, {
            cpm: plan.cpm, ctr: plan.ctr, atcRate: plan.atcRate,
            cvr: plan.organicCvr, aov: plan.organicAov, dailyBase: plan.organicDailyBase,
          })
        : [];
      const model = {
        aPurchase: plan.aPurchase, bPurchase: plan.bPurchase,
        aRevenue: plan.aPurchase * plan.aov, bRevenue: plan.bPurchase * plan.aov,
      };
      const curveRows = generateLogCurveWeeklyDays(SEED, `${key}-curve`, last8, plan.weeklyCosts, model, plan);
      return [...preRows, ...curveRows];
    }
    default:
      throw new Error(`알 수 없는 scenario: ${plan.scenario} (${key})`);
  }
}

async function main() {
  await mkdir(path.join(ROOT, "performance"), { recursive: true });
  await mkdir(path.join(ROOT, "journey"), { recursive: true });
  await mkdir(path.join(ROOT, "projects"), { recursive: true });
  await mkdir(path.join(ROOT, "xlsx"), { recursive: true });
  await mkdir(path.join(ROOT, "invalid-upload"), { recursive: true });

  const manifest = { seed: SEED, dateRange: [START_DATE, END_DATE], advertisers: [] };
  const projectsJson = { projects: [] };

  for (const adv of ADVERTISERS) {
    const perfRows = [];
    // projectType -> media -> { campaigns, firstCampaignId }
    const hierarchyByType = {};
    const campaignRegistry = {}; // projectType -> [{media, campaignId}]

    for (const projectType of PROJECT_TYPES) {
      const dateRangeForType = dateRange(...PROJECT_TYPE_RANGE[projectType]);
      hierarchyByType[projectType] = {};
      campaignRegistry[projectType] = [];

      for (const media of MEDIA_LIST) {
        const plan = mediaPlanFor(adv.id, projectType, media);
        if (!plan) continue;

        const campaignCount = adv.id === "aurora-beauty" && projectType === "ALWAYS" && media === "META" ? 4 : 2;
        const campaigns = buildHierarchy(adv.code, adv.name, media, projectType, { campaignCount });
        hierarchyByType[projectType][media] = campaigns;
        for (const camp of campaigns) {
          campaignRegistry[projectType].push({ media, campaignId: camp.campaignId });
        }

        const dayRows = generateMediaDayRows(adv.id, projectType, media, plan, dateRangeForType);
        const leaves = flattenLeaves(campaigns);
        for (const dayRow of dayRows) {
          if ((dayRow.cost ?? 0) === 0 && (dayRow.impressions ?? 0) === 0) continue; // 완전 비활동일은 행 자체를 만들지 않음
          const split = splitDayAcrossLeaves(dayRow, leaves);
          for (const s of split) {
            perfRows.push({
              date: dayRow.date, advertiser_id: adv.id, advertiser_name: adv.name, media,
              campaign_id: s.leaf.campaignId, campaign_name: s.leaf.campaignName,
              ad_group_id: s.leaf.adGroupId, ad_group_name: s.leaf.adGroupName,
              ad_id: s.leaf.adId, ad_name: s.leaf.adName,
              impressions: s.impressions, clicks: s.clicks, cost: s.cost,
              add_to_cart: s.addToCart, purchases: s.purchases, purchase_revenue: s.revenue,
            });
          }
        }
      }
    }

    perfRows.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
    await writeFile(
      path.join(ROOT, "performance", `performance_${adv.id}.csv`),
      rowsToCsv(PERFORMANCE_HEADER, perfRows),
      "utf-8",
    );

    for (const projectType of PROJECT_TYPES) {
      projectsJson.projects.push({
        advertiserId: adv.id,
        projectName: PROJECT_TYPE_LABEL[projectType],
        campaigns: campaignRegistry[projectType],
      });
    }

    // Journey: ALWAYS 프로젝트의 매체별 첫 캠페인을 기본 클릭 대상으로, PROMO_JULY의 첫 캠페인을
    // "다른 프로젝트 캠페인이 섞인 사용자"(요청 15번) 생성에 사용한다.
    const alwaysFirstCampaign = (media) => hierarchyByType.ALWAYS[media]?.[0]?.campaignId;
    const promoJulyFirstCampaign = (media) => hierarchyByType.PROMO_JULY[media]?.[0]?.campaignId;
    const alwaysDates = dateRange(...PROJECT_TYPE_RANGE.ALWAYS);
    const julyDates = dateRange(...PROJECT_TYPE_RANGE.PROMO_JULY);
    const avgAov = 42000;

    const journeyEvents = generateJourneyEvents(SEED, adv.id, adv.code, {
      purchaseDatesPool: alwaysDates,
      promoDatesPool: julyDates,
      campaignIdForMedia: alwaysFirstCampaign,
      promoCampaignIdForMedia: promoJulyFirstCampaign,
      journeyCount: 1600,
      aov: avgAov,
    });
    await writeFile(
      path.join(ROOT, "journey", `journey_${adv.id}.csv`),
      rowsToCsv(JOURNEY_HEADER, journeyEvents),
      "utf-8",
    );

    manifest.advertisers.push({
      advertiserId: adv.id, advertiserName: adv.name, summary: adv.summary,
      performanceRowCount: perfRows.length, journeyEventCount: journeyEvents.length,
    });
  }

  await writeFile(path.join(ROOT, "projects", "projects.json"), JSON.stringify(projectsJson, null, 2) + "\n", "utf-8");

  await writeXlsxFixtures();
  await writeInvalidUploadFixtures();

  console.log("[generate] 완료:");
  for (const a of manifest.advertisers) {
    console.log(`  - ${a.advertiserId}: performance ${a.performanceRowCount}행, journey ${a.journeyEventCount}건`);
  }
}

async function writeXlsxFixtures() {
  const perfSample = [
    PERFORMANCE_HEADER.map((h) => ({ value: h, fontWeight: "bold" })),
    ["2026-08-01", "aurora-beauty", "오로라뷰티", "META", "AUR-META-ALW-C01", "오로라뷰티 메타 상시 캠페인 1",
      "AUR-META-ALW-C01-G01", "오로라뷰티 메타 상시 캠페인 1 광고그룹 1", "AUR-META-ALW-C01-G01-A01",
      "오로라뷰티 메타 상시 캠페인 1 광고그룹 1 광고 1", 120000, 2400, 950000, 480, 55, 2500000]
      .map((v) => ({ value: v, type: typeof v === "number" ? Number : String })),
  ];
  await writeXlsxFile(perfSample, { filePath: path.join(ROOT, "xlsx", "performance_valid.xlsx") });

  const journeySample = [
    JOURNEY_HEADER.map((h) => ({ value: h, fontWeight: "bold" })),
    ["xlsx-e1", "xlsx-u1", "aurora-beauty", "CLICK", "2026-08-01T10:00:00Z", "META", "AUR-META-ALW-C01", "", "", "", ""]
      .map((v) => ({ value: v, type: String })),
    ["xlsx-e2", "xlsx-u1", "aurora-beauty", "PURCHASE", "2026-08-01T12:00:00Z", "", "", "", "", "xlsx-order-1", "45000"]
      .map((v) => ({ value: v, type: String })),
  ];
  await writeXlsxFile(journeySample, { filePath: path.join(ROOT, "xlsx", "journey_valid.xlsx") });

  // 2개 시트 파일: Backend는 항상 첫 번째 시트만 읽으므로, 두 번째 시트는 반드시 무시되어야 한다.
  await writeXlsxFile(
    [
      { data: perfSample, sheet: "Sheet1" },
      { data: [[{ value: "THIS_SHEET_MUST_BE_IGNORED", fontWeight: "bold" }]], sheet: "Sheet2_ShouldBeIgnored" },
    ],
    { filePath: path.join(ROOT, "xlsx", "xlsx_first_sheet_only_test.xlsx") },
  );
}

// 요청 21/22번: Backend 검증 코드를 실제로 읽어 확인한 정확한 오류 코드를 재현하는 11개
// 고정 Fixture. 정상 Seed 과정(seed.mjs)에서는 절대 자동 업로드하지 않는다.
async function writeInvalidUploadFixtures() {
  const dir = path.join(ROOT, "invalid-upload");
  const perfRow = (overrides = {}) => ({
    date: "2026-05-01", advertiser_id: "aurora-beauty", advertiser_name: "오로라뷰티", media: "META",
    campaign_id: "AUR-META-ALW-C01", campaign_name: "오로라뷰티 메타 상시 캠페인 1",
    ad_group_id: "AUR-META-ALW-C01-G01", ad_group_name: "오로라뷰티 메타 상시 캠페인 1 광고그룹 1",
    ad_id: "AUR-META-ALW-C01-G01-A01", ad_name: "오로라뷰티 메타 상시 캠페인 1 광고그룹 1 광고 1",
    impressions: 1500, clicks: 30, cost: 10000, add_to_cart: 10, purchases: 2, purchase_revenue: 60000,
    ...overrides,
  });

  // 1) REQUIRED_FIELD_MISSING(Performance): 필수 컬럼(add_to_cart)의 값이 비어 있음.
  await writeFile(
    path.join(dir, "performance_missing_required_column.csv"),
    rowsToCsv(PERFORMANCE_HEADER, [perfRow({ add_to_cart: "" })]),
    "utf-8",
  );

  // 2) INVALID_DATE: yyyy-MM-dd가 아닌 날짜 형식.
  await writeFile(
    path.join(dir, "performance_invalid_date.csv"),
    rowsToCsv(PERFORMANCE_HEADER, [perfRow({ date: "2026/05/01" })]),
    "utf-8",
  );

  // 3) NEGATIVE_VALUE: cost가 음수.
  await writeFile(
    path.join(dir, "performance_negative_cost.csv"),
    rowsToCsv(PERFORMANCE_HEADER, [perfRow({ cost: -10000 })]),
    "utf-8",
  );

  // 4) INVALID_NUMBER: 숫자 컬럼(impressions)에 숫자가 아닌 값.
  await writeFile(
    path.join(dir, "performance_non_numeric.csv"),
    rowsToCsv(PERFORMANCE_HEADER, [perfRow({ impressions: "abc" })]),
    "utf-8",
  );

  // 5) UNSUPPORTED_MEDIA: Media enum(META/TIKTOK/GOOGLE/NAVER/CRITEO)에 없는 값.
  await writeFile(
    path.join(dir, "performance_unsupported_media.csv"),
    rowsToCsv(PERFORMANCE_HEADER, [perfRow({ media: "KAKAO" })]),
    "utf-8",
  );

  // 6) DUPLICATE_NATURAL_KEY_IN_FILE(Performance): 같은 파일 안에 동일 natural key 2행.
  await writeFile(
    path.join(dir, "performance_duplicate_inside_file.csv"),
    rowsToCsv(PERFORMANCE_HEADER, [perfRow(), perfRow({ impressions: 1600, clicks: 32 })]),
    "utf-8",
  );

  // 7) DUPLICATE_CONFIRMATION_REQUIRED: 이미 SUCCESS로 존재하는 실제 시드 데이터의 natural key와
  // 동일(performance_aurora-beauty.csv의 첫 행)하되 수치만 다르게 만든 행. seed.mjs로 정상
  // 데이터를 먼저 업로드한 뒤 이 파일을 업로드하면 DUPLICATE_CONFIRMATION_REQUIRED 상태를 재현하며,
  // 이후 confirm/cancel 두 경로를 모두 테스트할 수 있다(요청 22번 Duplicate Overwrite Fixture 겸용).
  await writeFile(
    path.join(dir, "performance_duplicate_existing_data.csv"),
    rowsToCsv(PERFORMANCE_HEADER, [perfRow({ impressions: 999999, clicks: 9999, cost: 999999, purchases: 999 })]),
    "utf-8",
  );

  const journeyRow = (overrides = {}) => ({
    event_id: "FIX-E001", anonymous_user_id: "FIX-U001", advertiser_id: "aurora-beauty",
    event_type: "CLICK", event_timestamp: "2026-05-01T10:00:00Z",
    media: "META", campaign_id: "AUR-META-ALW-C01", ad_group_id: "", ad_id: "", order_id: "", purchase_revenue: "",
    ...overrides,
  });

  // 8) DUPLICATE_NATURAL_KEY_IN_FILE(Journey): 같은 파일 안에 advertiser_id+event_id가 동일한 2행.
  await writeFile(
    path.join(dir, "journey_duplicate_event_id.csv"),
    rowsToCsv(JOURNEY_HEADER, [journeyRow(), journeyRow({ event_timestamp: "2026-05-01T11:00:00Z" })]),
    "utf-8",
  );

  // 9) 검증 결과: order_id는 natural key(advertiser_id+event_id)에 포함되지 않아 실제로는 오류가
  // 발생하지 않는다(코드 확인 완료). "정상 업로드됨"을 확인하는 참고용 Fixture로 문서화한다.
  await writeFile(
    path.join(dir, "journey_duplicate_order_id.csv"),
    rowsToCsv(JOURNEY_HEADER, [
      journeyRow({ event_id: "FIX-E002", event_type: "PURCHASE", event_timestamp: "2026-05-01T12:00:00Z", media: "", campaign_id: "", order_id: "FIX-ORDER-DUP", purchase_revenue: "50000" }),
      journeyRow({ event_id: "FIX-E003", anonymous_user_id: "FIX-U002", event_type: "PURCHASE", event_timestamp: "2026-05-02T12:00:00Z", media: "", campaign_id: "", order_id: "FIX-ORDER-DUP", purchase_revenue: "70000" }),
    ]),
    "utf-8",
  );

  // 10) INVALID_EVENT_TYPE: CLICK/PURCHASE가 아닌 값.
  await writeFile(
    path.join(dir, "journey_invalid_event_type.csv"),
    rowsToCsv(JOURNEY_HEADER, [journeyRow({ event_type: "VIEW" })]),
    "utf-8",
  );

  // 11) REQUIRED_FIELD_MISSING(Journey): anonymous_user_id가 비어 있음.
  await writeFile(
    path.join(dir, "journey_missing_user_id.csv"),
    rowsToCsv(JOURNEY_HEADER, [journeyRow({ anonymous_user_id: "" })]),
    "utf-8",
  );
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
