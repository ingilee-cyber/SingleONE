import { rngFor, uniform } from "./rng.mjs";
import { toTimestamp, addHoursToTimestamp } from "./dates.mjs";

// 채널 시퀀스는 "구매로부터 몇 시간 전"인지(hoursBefore, 큰 값=오래전) 기준으로 적는다.
// 코드 확인: window는 purchaseTime-7일(=168시간) ~ purchaseTime 양끝 포함.
const PATTERNS = {
  single_meta: { touches: [{ media: "META", hoursBefore: 3 }] },
  single_google: { touches: [{ media: "GOOGLE", hoursBefore: 4 }] },
  two_meta_google: { touches: [{ media: "META", hoursBefore: 48 }, { media: "GOOGLE", hoursBefore: 5 }] },
  two_tiktok_meta: { touches: [{ media: "TIKTOK", hoursBefore: 72 }, { media: "META", hoursBefore: 6 }] },
  three_meta_google_criteo: {
    touches: [{ media: "META", hoursBefore: 96 }, { media: "GOOGLE", hoursBefore: 48 }, { media: "CRITEO", hoursBefore: 5 }],
  },
  three_tiktok_meta_google: {
    touches: [{ media: "TIKTOK", hoursBefore: 100 }, { media: "META", hoursBefore: 50 }, { media: "GOOGLE", hoursBefore: 3 }],
  },
  four_naver_meta_google_criteo: {
    touches: [
      { media: "NAVER", hoursBefore: 150 }, { media: "META", hoursBefore: 100 },
      { media: "GOOGLE", hoursBefore: 50 }, { media: "CRITEO", hoursBefore: 4 },
    ],
  },
  repeated_meta_meta_google: {
    // 연속 동일 채널(META,META) — Path 시각화에서는 압축되지만 raw event는 2건 그대로 유지.
    touches: [{ media: "META", hoursBefore: 80 }, { media: "META", hoursBefore: 79 }, { media: "GOOGLE", hoursBefore: 3 }],
  },
  reappear_meta_google_meta: {
    // 비연속 채널 재등장 — unique channel 기준 Attribution 검증용.
    touches: [{ media: "META", hoursBefore: 90 }, { media: "GOOGLE", hoursBefore: 50 }, { media: "META", hoursBefore: 3 }],
  },
  // 채널 페어 다양화를 위한 브랜드별 조합.
  two_naver_criteo: { touches: [{ media: "NAVER", hoursBefore: 40 }, { media: "CRITEO", hoursBefore: 4 }] },
  two_naver_google: { touches: [{ media: "NAVER", hoursBefore: 36 }, { media: "GOOGLE", hoursBefore: 3 }] },
};

// 요청 16번: 브랜드별 Top Path 성격 차별화.
export const BRAND_PATTERN_WEIGHTS = {
  "aurora-beauty": [
    ["two_meta_google", 30], ["single_meta", 18], ["three_tiktok_meta_google", 14],
    ["two_tiktok_meta", 12], ["single_google", 8], ["reappear_meta_google_meta", 8],
    ["repeated_meta_meta_google", 6], ["three_meta_google_criteo", 4],
  ],
  "urban-fit": [
    ["single_google", 30], ["two_meta_google", 22], ["single_meta", 14],
    ["three_meta_google_criteo", 12], ["two_tiktok_meta", 8], ["reappear_meta_google_meta", 6],
    ["four_naver_meta_google_criteo", 4], ["repeated_meta_meta_google", 4],
  ],
  "living-lab": [
    ["two_naver_criteo", 28], ["two_naver_google", 20], ["single_google", 12],
    ["four_naver_meta_google_criteo", 12], ["two_meta_google", 10], ["three_meta_google_criteo", 8],
    ["reappear_meta_google_meta", 6], ["repeated_meta_meta_google", 4],
  ],
};

function weightedPick(rng, weighted) {
  const total = weighted.reduce((s, [, w]) => s + w, 0);
  let r = uniform(rng, 0, total);
  for (const [key, w] of weighted) {
    if (r < w) return key;
    r -= w;
  }
  return weighted[weighted.length - 1][0];
}

/**
 * 하나의 구매 Journey(클릭 이벤트 배열 + 구매 이벤트)를 만든다.
 * @param purchaseTimestampISO 구매 시각(ISO, Z)
 * @param campaignIdForMedia media -> campaignId 매핑(실제 Performance 캠페인과 문자열 일치)
 */
function buildJourneyEvents({ patternKey, purchaseTimestampISO, userId, orderId, revenue, campaignIdForMedia, idGen }) {
  const pattern = PATTERNS[patternKey];
  const events = [];
  for (const touch of pattern.touches) {
    const ts = addHoursToTimestamp(purchaseTimestampISO, -touch.hoursBefore);
    const campaignId = campaignIdForMedia(touch.media);
    events.push({
      event_id: idGen.next(),
      anonymous_user_id: userId,
      advertiser_id: idGen.advertiserId,
      event_type: "CLICK",
      event_timestamp: ts,
      media: touch.media,
      campaign_id: campaignId,
      ad_group_id: "",
      ad_id: "",
      order_id: "",
      purchase_revenue: "",
    });
  }
  events.push({
    event_id: idGen.next(),
    anonymous_user_id: userId,
    advertiser_id: idGen.advertiserId,
    event_type: "PURCHASE",
    event_timestamp: purchaseTimestampISO,
    media: "",
    campaign_id: "",
    ad_group_id: "",
    ad_id: "",
    order_id: orderId,
    purchase_revenue: revenue,
  });
  return events;
}

export function makeIdGenerator(advertiserId, advertiserCode) {
  let eventSeq = 0;
  let orderSeq = 0;
  return {
    advertiserId,
    next() {
      eventSeq += 1;
      return `${advertiserCode}-E${String(eventSeq).padStart(6, "0")}`;
    },
    nextOrderId() {
      orderSeq += 1;
      return `${advertiserCode}-ORDER-${String(orderSeq).padStart(6, "0")}`;
    },
  };
}

/**
 * 요청 12~18번을 만족하는 Journey 이벤트 전체를 만든다.
 * @param opts.purchaseDatesPool 구매가 일어날 수 있는 날짜(ISO) 배열(성과 데이터 기간과 맞춤)
 * @param opts.campaignIdForMedia media -> campaignId
 * @param opts.promoCampaignIdForMedia 7월 프로모션 campaign_id(프로젝트별 필터링 검증용, optional)
 * @param opts.journeyCount 생성할 일반 구매 Journey 수(가중치 기반 패턴 샘플링)
 * @param opts.aov 매체 무관 평균 구매금액(단순화; 매체별 revenue는 패턴 내 재현 안 하고 여기서 결정)
 */
export function generateJourneyEvents(seed, advertiserId, advertiserCode, opts) {
  const rng = rngFor(seed, `journey-${advertiserId}`);
  const idGen = makeIdGenerator(advertiserId, advertiserCode);
  const weights = BRAND_PATTERN_WEIGHTS[advertiserId];
  const events = [];
  let userSeq = 0;
  const nextUser = () => {
    userSeq += 1;
    return `${advertiserCode}-U${String(userSeq).padStart(6, "0")}`;
  };

  const pool = opts.purchaseDatesPool;
  const pick = (arr) => arr[Math.floor(uniform(rng, 0, arr.length))];

  // 1) 일반 구매 Journey(가중치 패턴 샘플링) — 대부분의 물량을 차지.
  for (let i = 0; i < opts.journeyCount; i++) {
    const patternKey = weightedPick(rng, weights);
    const dateISO = pick(pool);
    const purchaseTs = toTimestamp(dateISO, Math.floor(uniform(rng, 9, 22)));
    const userId = nextUser();
    const revenue = Math.round(opts.aov * uniform(rng, 0.6, 1.6));
    events.push(
      ...buildJourneyEvents({
        patternKey,
        purchaseTimestampISO: purchaseTs,
        userId,
        orderId: idGen.nextOrderId(),
        revenue,
        campaignIdForMedia: opts.campaignIdForMedia,
        idGen,
      }),
    );
  }

  // 2) 다중 구매 사용자(요청 13.10) — 동일 사용자가 서로 다른 날짜에 2회 이상 구매.
  //    직전 구매 이후 클릭만 다음 Journey에 재사용되도록, 두 번째 구매의 클릭은 첫 구매 이후
  //    시각으로만 배치한다.
  const repeatBuyerCount = Math.max(1, Math.round(opts.journeyCount * 0.05));
  for (let i = 0; i < repeatBuyerCount; i++) {
    const userId = nextUser();
    const sortedDates = [...pool].sort();
    const firstIdx = Math.floor(uniform(rng, 0, sortedDates.length * 0.5));
    const secondIdx = Math.min(sortedDates.length - 1, firstIdx + 10 + Math.floor(uniform(rng, 0, 20)));
    const firstDate = sortedDates[firstIdx];
    const secondDate = sortedDates[secondIdx];
    const firstTs = toTimestamp(firstDate, 12);
    const secondTs = toTimestamp(secondDate, 15);
    const patternA = weightedPick(rng, weights);
    const patternB = weightedPick(rng, weights);
    events.push(
      ...buildJourneyEvents({
        patternKey: patternA, purchaseTimestampISO: firstTs, userId,
        orderId: idGen.nextOrderId(), revenue: Math.round(opts.aov * uniform(rng, 0.6, 1.6)),
        campaignIdForMedia: opts.campaignIdForMedia, idGen,
      }),
      ...buildJourneyEvents({
        patternKey: patternB, purchaseTimestampISO: secondTs, userId,
        orderId: idGen.nextOrderId(), revenue: Math.round(opts.aov * uniform(rng, 0.6, 1.6)),
        campaignIdForMedia: opts.campaignIdForMedia, idGen,
      }),
    );
  }

  // 3) 7일 경계 Edge Case(요청 14번) — 전체의 소수 비율.
  const boundaryDate = pool[Math.floor(pool.length * 0.6)];
  const boundaryTs = toTimestamp(boundaryDate, 12);
  // A. 6일23시간 전 클릭 -> 포함.
  events.push(
    ...buildJourneyEvents({
      patternKey: "single_meta",
      purchaseTimestampISO: boundaryTs,
      userId: nextUser(),
      orderId: idGen.nextOrderId(),
      revenue: Math.round(opts.aov),
      campaignIdForMedia: opts.campaignIdForMedia,
      idGen,
    }).map((e) => (e.event_type === "CLICK" ? { ...e, event_timestamp: addHoursToTimestamp(boundaryTs, -(6 * 24 + 23)) } : e)),
  );
  // B. 7일을 초과한 클릭 -> 제외(해당 클릭만 있고 window 밖이라 attributed 목록에서 빠짐).
  const excludedUser = nextUser();
  events.push({
    event_id: idGen.next(), anonymous_user_id: excludedUser, advertiser_id: advertiserId,
    event_type: "CLICK", event_timestamp: addHoursToTimestamp(boundaryTs, -(7 * 24 + 2)),
    media: "META", campaign_id: opts.campaignIdForMedia("META"), ad_group_id: "", ad_id: "", order_id: "", purchase_revenue: "",
  });
  events.push({
    event_id: idGen.next(), anonymous_user_id: excludedUser, advertiser_id: advertiserId,
    event_type: "PURCHASE", event_timestamp: boundaryTs,
    media: "", campaign_id: "", ad_group_id: "", ad_id: "",
    order_id: idGen.nextOrderId(), purchase_revenue: Math.round(opts.aov),
  });
  // C. 8일 전(제외) + 2일전 TikTok + 1일전 TikTok(연속 동일 채널, 압축 대상) -> 최종 TIKTOK만 유효.
  const compressUser = nextUser();
  const compressTs = boundaryTs;
  events.push({
    event_id: idGen.next(), anonymous_user_id: compressUser, advertiser_id: advertiserId,
    event_type: "CLICK", event_timestamp: addHoursToTimestamp(compressTs, -8 * 24),
    media: "META", campaign_id: opts.campaignIdForMedia("META"), ad_group_id: "", ad_id: "", order_id: "", purchase_revenue: "",
  });
  events.push({
    event_id: idGen.next(), anonymous_user_id: compressUser, advertiser_id: advertiserId,
    event_type: "CLICK", event_timestamp: addHoursToTimestamp(compressTs, -48),
    media: "TIKTOK", campaign_id: opts.campaignIdForMedia("TIKTOK"), ad_group_id: "", ad_id: "", order_id: "", purchase_revenue: "",
  });
  events.push({
    event_id: idGen.next(), anonymous_user_id: compressUser, advertiser_id: advertiserId,
    event_type: "CLICK", event_timestamp: addHoursToTimestamp(compressTs, -24),
    media: "TIKTOK", campaign_id: opts.campaignIdForMedia("TIKTOK"), ad_group_id: "", ad_id: "", order_id: "", purchase_revenue: "",
  });
  events.push({
    event_id: idGen.next(), anonymous_user_id: compressUser, advertiser_id: advertiserId,
    event_type: "PURCHASE", event_timestamp: compressTs,
    media: "", campaign_id: "", ad_group_id: "", ad_id: "",
    order_id: idGen.nextOrderId(), purchase_revenue: Math.round(opts.aov),
  });

  // 4) 프로젝트 필터링 검증(요청 15번) — 상시+프로모션 캠페인 클릭이 섞인 사용자 일부.
  if (opts.promoCampaignIdForMedia) {
    const mixCount = Math.max(1, Math.round(opts.journeyCount * 0.03));
    for (let i = 0; i < mixCount; i++) {
      const dateISO = pick(opts.promoDatesPool ?? pool);
      const purchaseTs = toTimestamp(dateISO, 14);
      const userId = nextUser();
      events.push(
        {
          event_id: idGen.next(), anonymous_user_id: userId, advertiser_id: advertiserId,
          event_type: "CLICK", event_timestamp: addHoursToTimestamp(purchaseTs, -72),
          media: "META", campaign_id: opts.campaignIdForMedia("META"), ad_group_id: "", ad_id: "", order_id: "", purchase_revenue: "",
        },
        {
          event_id: idGen.next(), anonymous_user_id: userId, advertiser_id: advertiserId,
          event_type: "CLICK", event_timestamp: addHoursToTimestamp(purchaseTs, -5),
          media: "GOOGLE", campaign_id: opts.promoCampaignIdForMedia("GOOGLE"), ad_group_id: "", ad_id: "", order_id: "", purchase_revenue: "",
        },
        {
          event_id: idGen.next(), anonymous_user_id: userId, advertiser_id: advertiserId,
          event_type: "PURCHASE", event_timestamp: purchaseTs,
          media: "", campaign_id: "", ad_group_id: "", ad_id: "",
          order_id: idGen.nextOrderId(), purchase_revenue: Math.round(opts.aov),
        },
      );
    }
  }

  return events;
}
