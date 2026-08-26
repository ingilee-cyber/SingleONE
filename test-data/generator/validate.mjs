// 요청 30번의 ~20개 불변식을 검사한다. 하나라도 실패하면 exit code 1로 종료하며,
// 이 경우 데이터 생성 작업을 완료로 보고하지 않는다(요청 30번 규칙).
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { ADVERTISERS } from "./lib/brands.mjs";
import { parseCsv } from "./lib/csv.mjs";
import { MEDIA_LIST, INTERNAL_FILTER_RATE, INDEX_MIN_COST, INDEX_MIN_PURCHASES, INDEX_MIN_OPERATING_DAYS } from "./lib/constants.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..", "demo-full");

const failures = [];
const passes = [];
function check(name, condition, detail = "") {
  if (condition) {
    passes.push(name);
  } else {
    failures.push(`${name}${detail ? ` — ${detail}` : ""}`);
  }
}

async function loadAdvertiserData(adv) {
  const perfText = await readFile(path.join(ROOT, "performance", `performance_${adv.id}.csv`), "utf-8");
  const journeyText = await readFile(path.join(ROOT, "journey", `journey_${adv.id}.csv`), "utf-8");
  return { perf: parseCsv(perfText), journey: parseCsv(journeyText) };
}

function hoursBetween(aIso, bIso) {
  return (new Date(bIso).getTime() - new Date(aIso).getTime()) / 3600000;
}

async function main() {
  // 1. advertiser >= 3
  check("advertiser >= 3", ADVERTISERS.length >= 3, `실제 ${ADVERTISERS.length}`);

  // 3. project definition 3개 존재(advertiser별)
  const projectsJson = JSON.parse(await readFile(path.join(ROOT, "projects", "projects.json"), "utf-8"));
  for (const adv of ADVERTISERS) {
    const count = projectsJson.projects.filter((p) => p.advertiserId === adv.id).length;
    check(`[${adv.id}] project definition 3개 존재`, count === 3, `실제 ${count}`);
  }

  for (const adv of ADVERTISERS) {
    const { perf, journey } = await loadAdvertiserData(adv);

    // 2. 운영기간 >= 90 days
    const dates = perf.map((r) => r.date).sort();
    const spanDays = (new Date(dates[dates.length - 1]) - new Date(dates[0])) / 86400000 + 1;
    check(`[${adv.id}] 운영기간 >= 90일`, spanDays >= 90, `실제 ${spanDays}일`);

    // 4. ID unique 규칙(campaign_id는 advertiser+media, ad_group_id는 +campaign, ad_id는 +ad_group 내에서만 unique)
    const campaignByMedia = new Map(); // media -> Set(campaignId) 자체는 unique할 필요 없음(우리가 그렇게 부여)
    const groupOwners = new Map(); // adGroupId -> Set("media|campaignId")
    const adOwners = new Map(); // adId -> Set("media|campaignId|adGroupId")
    for (const r of perf) {
      const gKey = r.ad_group_id;
      const gOwner = `${r.media}|${r.campaign_id}`;
      if (!groupOwners.has(gKey)) groupOwners.set(gKey, new Set());
      groupOwners.get(gKey).add(gOwner);
      const aKey = r.ad_id;
      const aOwner = `${r.media}|${r.campaign_id}|${r.ad_group_id}`;
      if (!adOwners.has(aKey)) adOwners.set(aKey, new Set());
      adOwners.get(aKey).add(aOwner);
    }
    const badGroups = [...groupOwners.entries()].filter(([, owners]) => owners.size > 1);
    const badAds = [...adOwners.entries()].filter(([, owners]) => owners.size > 1);
    check(`[${adv.id}] ad_group_id가 (media,campaign) 범위를 넘어 재사용되지 않음`, badGroups.length === 0, `위반 ${badGroups.length}건`);
    check(`[${adv.id}] ad_id가 (media,campaign,ad_group) 범위를 넘어 재사용되지 않음`, badAds.length === 0, `위반 ${badAds.length}건`);

    // 5. Performance Natural Key duplicate 없음
    const naturalKeys = new Set();
    let dupCount = 0;
    for (const r of perf) {
      const key = `${r.date}|${r.advertiser_id}|${r.media}|${r.campaign_id}|${r.ad_group_id}|${r.ad_id}`;
      if (naturalKeys.has(key)) dupCount++;
      naturalKeys.add(key);
    }
    check(`[${adv.id}] Performance Natural Key duplicate 없음`, dupCount === 0, `중복 ${dupCount}건`);

    // 6. Journey event_id duplicate 없음(advertiser_id+event_id)
    const eventIds = new Set();
    let eventDup = 0;
    for (const j of journey) {
      const key = `${j.advertiser_id}|${j.event_id}`;
      if (eventIds.has(key)) eventDup++;
      eventIds.add(key);
    }
    check(`[${adv.id}] Journey event_id duplicate 없음`, eventDup === 0, `중복 ${eventDup}건`);

    // 7. order_id unique(빈 값 제외)
    const orderIds = new Set();
    let orderDup = 0;
    for (const j of journey) {
      if (!j.order_id) continue;
      if (orderIds.has(j.order_id)) orderDup++;
      orderIds.add(j.order_id);
    }
    check(`[${adv.id}] order_id unique(정상 데이터셋 내)`, orderDup === 0, `중복 ${orderDup}건`);

    // 8. unsupported media 없음
    const badMedia = perf.filter((r) => !MEDIA_LIST.includes(r.media));
    check(`[${adv.id}] unsupported media 없음`, badMedia.length === 0, `위반 ${badMedia.length}건`);

    // 9/10/11/12. 음수 없음 + clicks<=impressions + purchases<=clicks + revenue>=0
    let negCount = 0, clicksOverImpr = 0, purchOverClicks = 0;
    for (const r of perf) {
      const impressions = Number(r.impressions), clicks = Number(r.clicks), cost = Number(r.cost);
      const addToCart = Number(r.add_to_cart), purchases = Number(r.purchases), revenue = Number(r.purchase_revenue);
      if ([impressions, clicks, cost, addToCart, purchases, revenue].some((v) => v < 0)) negCount++;
      if (clicks > impressions) clicksOverImpr++;
      if (purchases > clicks) purchOverClicks++;
    }
    check(`[${adv.id}] negative metric 없음`, negCount === 0, `위반 ${negCount}건`);
    check(`[${adv.id}] clicks <= impressions`, clicksOverImpr === 0, `위반 ${clicksOverImpr}건`);
    check(`[${adv.id}] purchases <= clicks`, purchOverClicks === 0, `위반 ${purchOverClicks}건`);

    // 13. PURCHASE 이후 Click이 같은 Journey에 들어가지 않음(사용자의 마지막 구매 이후 클릭 없음)
    const lastPurchaseByUser = new Map();
    for (const j of journey) {
      if (j.event_type !== "PURCHASE") continue;
      const cur = lastPurchaseByUser.get(j.anonymous_user_id);
      if (!cur || j.event_timestamp > cur) lastPurchaseByUser.set(j.anonymous_user_id, j.event_timestamp);
    }
    let clickAfterLastPurchase = 0;
    for (const j of journey) {
      if (j.event_type !== "CLICK") continue;
      const lastPurchase = lastPurchaseByUser.get(j.anonymous_user_id);
      if (lastPurchase && j.event_timestamp > lastPurchase) clickAfterLastPurchase++;
    }
    check(`[${adv.id}] 사용자의 마지막 구매 이후 Click 없음`, clickAfterLastPurchase === 0, `위반 ${clickAfterLastPurchase}건`);

    // 14. Journey Click Campaign이 실제 Campaign Master(Performance)에 존재
    const knownCampaigns = new Set(perf.map((r) => `${r.media}|${r.campaign_id}`));
    const unknownClickCampaigns = journey.filter(
      (j) => j.event_type === "CLICK" && !knownCampaigns.has(`${j.media}|${j.campaign_id}`),
    );
    check(`[${adv.id}] Journey Click Campaign이 Campaign Master에 존재`, unknownClickCampaigns.length === 0, `미존재 ${unknownClickCampaigns.length}건`);

    // 15~19. Journey 패턴 다양성 검사(7일 경계/다중구매/멀티채널/반복채널/3채널 이상)
    const eventsByUser = new Map();
    for (const j of journey) {
      if (!eventsByUser.has(j.anonymous_user_id)) eventsByUser.set(j.anonymous_user_id, []);
      eventsByUser.get(j.anonymous_user_id).push(j);
    }
    let hasBoundaryIncluded = false, hasBoundaryExcluded = false;
    let hasMultiPurchaseUser = false, hasMultiChannel = false, hasRepeatedChannel = false, has3PlusChannel = false;
    for (const [, events] of eventsByUser) {
      const purchases = events.filter((e) => e.event_type === "PURCHASE");
      if (purchases.length >= 2) hasMultiPurchaseUser = true;
      for (const p of purchases) {
        const clicks = events.filter((e) => e.event_type === "CLICK");
        const windowClicks = clicks.filter((c) => {
          const diff = hoursBetween(c.event_timestamp, p.event_timestamp);
          return diff >= 0 && diff <= 168;
        });
        const rawChannels = windowClicks
          .filter((c) => hoursBetween(c.event_timestamp, p.event_timestamp) <= 168)
          .sort((a, b) => (a.event_timestamp < b.event_timestamp ? -1 : 1))
          .map((c) => c.media);
        const uniqueChannels = new Set(rawChannels);
        if (uniqueChannels.size >= 2) hasMultiChannel = true;
        if (uniqueChannels.size >= 3) has3PlusChannel = true;
        const mediaCounts = {};
        for (const m of rawChannels) mediaCounts[m] = (mediaCounts[m] ?? 0) + 1;
        if (Object.values(mediaCounts).some((c) => c >= 2)) hasRepeatedChannel = true;
        for (const c of clicks) {
          const diff = hoursBetween(c.event_timestamp, p.event_timestamp);
          if (diff >= 166.9 && diff <= 168) hasBoundaryIncluded = true;
          if (diff > 168) hasBoundaryExcluded = true;
        }
      }
    }
    check(`[${adv.id}] 7일 경계(포함) 케이스 존재`, hasBoundaryIncluded);
    check(`[${adv.id}] 7일 경계(제외) 케이스 존재`, hasBoundaryExcluded);
    check(`[${adv.id}] multiple purchase user 존재`, hasMultiPurchaseUser);
    check(`[${adv.id}] multi-channel Journey 존재`, hasMultiChannel);
    check(`[${adv.id}] repeated-channel Journey 존재`, hasRepeatedChannel);
    check(`[${adv.id}] 3-channel 이상 Journey 존재`, has3PlusChannel);

    // 20. Simulation용 8주 이상 데이터 존재(해당 advertiser 중 하나 이상 매체가 cost>0인 서로 다른 주 8개 이상)
    const costDaysByMedia = new Map();
    for (const r of perf) {
      if (Number(r.cost) <= 0) continue;
      if (!costDaysByMedia.has(r.media)) costDaysByMedia.set(r.media, new Set());
      costDaysByMedia.get(r.media).add(r.date);
    }
    const anyMediaWith8Weeks = [...costDaysByMedia.values()].some((days) => days.size >= 56);
    check(`[${adv.id}] Simulation용 8주(56일) 이상 cost 데이터를 가진 매체 존재`, anyMediaWith8Weeks);
  }

  // 21. 데이터 부족 Case 존재(living-lab 기준: Index는 Project 단위로 계산되므로 프로젝트별로
  // 분리 집계해야 한다 — 광고주 전체로 합치면 다른 프로젝트의 정상 데이터에 가려질 수 있음).
  const living = ADVERTISERS.find((a) => a.id === "living-lab");
  const { perf: livingPerf } = await loadAdvertiserData(living);
  const byProjectMedia = new Map(); // "PROJECT_CODE|media" -> {cost,days,rawPurchases}
  for (const r of livingPerf) {
    const projCodeMatch = r.campaign_id.match(/-(ALW|P07|P08)-/);
    const projCode = projCodeMatch ? projCodeMatch[1] : "UNKNOWN";
    const key = `${projCode}|${r.media}`;
    if (!byProjectMedia.has(key)) byProjectMedia.set(key, { cost: 0, days: new Set(), rawPurchases: 0 });
    const m = byProjectMedia.get(key);
    m.cost += Number(r.cost);
    if (Number(r.cost) > 0) m.days.add(r.date);
    m.rawPurchases += Number(r.purchases);
  }
  let hasInsufficientMedia = false;
  for (const [key, m] of byProjectMedia) {
    const media = key.split("|")[1];
    const filtered = m.rawPurchases * INTERNAL_FILTER_RATE[media];
    if (m.cost < INDEX_MIN_COST || m.days.size < INDEX_MIN_OPERATING_DAYS || filtered < INDEX_MIN_PURCHASES) {
      hasInsufficientMedia = true;
    }
  }
  check("[living-lab] SingleONE Index 데이터 부족 Case(B/C/D/E 중 하나 이상) 존재", hasInsufficientMedia);

  // 22. 대표 Demo Brand(aurora-beauty) 상시 프로젝트가 5개 매체 전부 정상 Index 계산 가능 조건 충족
  const aurora = ADVERTISERS.find((a) => a.id === "aurora-beauty");
  const { perf: auroraPerf } = await loadAdvertiserData(aurora);
  const alwaysByMedia = new Map();
  for (const r of auroraPerf) {
    if (!r.campaign_id.includes("-ALW-")) continue;
    if (!alwaysByMedia.has(r.media)) alwaysByMedia.set(r.media, { cost: 0, days: new Set(), rawPurchases: 0 });
    const m = alwaysByMedia.get(r.media);
    m.cost += Number(r.cost);
    if (Number(r.cost) > 0) m.days.add(r.date);
    m.rawPurchases += Number(r.purchases);
  }
  let allValid = alwaysByMedia.size === MEDIA_LIST.length;
  for (const [media, m] of alwaysByMedia) {
    const filtered = m.rawPurchases * INTERNAL_FILTER_RATE[media];
    if (m.cost < INDEX_MIN_COST || m.days.size < INDEX_MIN_OPERATING_DAYS || filtered < INDEX_MIN_PURCHASES) {
      allValid = false;
      failures.push(`[aurora-beauty] ALWAYS ${media}가 Index 유효 조건 미달(cost=${m.cost}, days=${m.days.size}, filteredPurchases=${filtered.toFixed(1)})`);
    }
  }
  check("[aurora-beauty] 상시 프로젝트 5개 매체 전부 정상 Index 계산 가능", allValid);

  console.log(`[validate] PASS ${passes.length}건, FAIL ${failures.length}건\n`);
  if (failures.length > 0) {
    console.error("실패 항목:");
    for (const f of failures) console.error(`  - ${f}`);
    process.exit(1);
  } else {
    console.log("모든 검증 통과.");
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
