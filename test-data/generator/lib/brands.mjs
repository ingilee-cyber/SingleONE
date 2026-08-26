// 광고주 3곳 + 매체별 특성 + 의도적 Edge Case 배치. 모든 숫자는 이 파일에서만 바뀌면
// generate.mjs가 그대로 반영하므로, 재현성 검증 후 튜닝이 필요하면 여기만 고치면 된다.

export const ADVERTISERS = [
  {
    id: "aurora-beauty",
    name: "오로라뷰티",
    code: "AUR",
    summary: "Meta/TikTok 성과가 상대적으로 강하고 프로모션 반응이 큰 뷰티 브랜드. 상시 프로젝트는 5개 매체 모두 정상 Index 계산이 가능하고 Simulation도 3개 매체가 예측 가능하도록 구성한 대표 시연용 광고주.",
  },
  {
    id: "urban-fit",
    name: "어반핏",
    code: "URB",
    summary: "Google/Meta 성과가 상대적으로 강하고 검색·리타겟팅 비중이 높은 피트니스 브랜드.",
  },
  {
    id: "living-lab",
    name: "리빙랩",
    code: "LIV",
    summary: "Naver/Criteo 비중이 높은 리빙 브랜드. 상시/8월 프로모션 프로젝트에 SingleONE Index 데이터 부족 Edge Case를 의도적으로 배치했다.",
  },
];

// 프로모션(ORGANIC) 캠페인을 만들 때 참조하는 "상시" 기준값(먼저 정의해야 아래에서 참조 가능).
const ALWAYS_BASE = {
  "aurora-beauty": {
    META: { organicDailyBase: 70000, organicCvr: 0.045, organicAov: 45000, cpm: 7000, ctr: 0.018, atcRate: 0.35 },
    TIKTOK: { organicDailyBase: 55000, organicCvr: 0.05, organicAov: 38000, cpm: 6000, ctr: 0.022, atcRate: 0.4 },
    GOOGLE: { organicDailyBase: 60000, organicCvr: 0.04, organicAov: 52000, cpm: 8500, ctr: 0.02, atcRate: 0.3 },
    NAVER: { dailyBase: 380000 / 7, cvr: 0.038, aov: 40000, cpm: 5500, ctr: 0.015, atcRate: 0.32 },
    CRITEO: { organicDailyBase: 58000, organicCvr: 0.035, organicAov: 42000, cpm: 6500, ctr: 0.017, atcRate: 0.3 },
  },
  "urban-fit": {
    META: { dailyBase: 68000, cvr: 0.04, aov: 44000, cpm: 7200, ctr: 0.02, atcRate: 0.33 },
    GOOGLE: { dailyBase: 75000, cvr: 0.042, aov: 48000, cpm: 8000, ctr: 0.024, atcRate: 0.32 },
    TIKTOK: { dailyBase: 35000, cvr: 0.03, aov: 36000, cpm: 6200, ctr: 0.017, atcRate: 0.28 },
    NAVER: { dailyBase: 30000, cvr: 0.028, aov: 39000, cpm: 5800, ctr: 0.014, atcRate: 0.25 },
    CRITEO: { dailyBase: 28000, cvr: 0.027, aov: 41000, cpm: 6800, ctr: 0.015, atcRate: 0.26 },
  },
};

// 프로모션 기간은 상시보다 예산을 끌어올린 organic 캠페인으로 구성한다(요청 8번: 프로모션 영향).
function promoOrganic(advertiserId, boostByMedia) {
  const base = ALWAYS_BASE[advertiserId];
  const out = {};
  for (const media of Object.keys(boostByMedia)) {
    const b = base[media];
    const dailyBase = (b.organicDailyBase ?? b.dailyBase ?? 40000) * boostByMedia[media];
    out[media] = {
      scenario: "ORGANIC",
      cpm: b.cpm, ctr: b.ctr, atcRate: b.atcRate,
      cvr: b.organicCvr ?? b.cvr ?? 0.035,
      aov: b.organicAov ?? b.aov ?? 40000,
      dailyBase,
    };
  }
  return out;
}

// scenario: "ORGANIC" | "LOG_CURVE" | "FLAT" | "SPARSE" | "ACTIVE_ONLY" | "ZERO_PURCHASE" | "LOW_PURCHASE"
export const MEDIA_PLANS = {
  "aurora-beauty": {
    ALWAYS: {
      META: {
        scenario: "LOG_CURVE",
        cpm: 7000, ctr: 0.018, atcRate: 0.35,
        organicDailyBase: 70000, organicCvr: 0.045, organicAov: 45000,
        weeklyCosts: [600000, 650000, 700000, 760000, 820000, 880000, 950000, 1050000],
        aPurchase: 90, bPurchase: -1127, aov: 480,
      },
      TIKTOK: {
        scenario: "LOG_CURVE",
        cpm: 6000, ctr: 0.022, atcRate: 0.4,
        organicDailyBase: 55000, organicCvr: 0.05, organicAov: 38000,
        weeklyCosts: [450000, 480000, 520000, 560000, 610000, 660000, 720000, 800000],
        aPurchase: 95, bPurchase: -1172, aov: 400,
      },
      GOOGLE: {
        scenario: "LOG_CURVE",
        cpm: 8500, ctr: 0.02, atcRate: 0.3,
        organicDailyBase: 60000, organicCvr: 0.04, organicAov: 52000,
        weeklyCosts: [500000, 540000, 580000, 630000, 680000, 740000, 810000, 900000],
        aPurchase: 70, bPurchase: -873, aov: 620,
      },
      NAVER: {
        scenario: "FLAT",
        cpm: 5500, ctr: 0.015, atcRate: 0.32, cvr: 0.038, aov: 40000,
        weeklyCostBase: 380000,
      },
      CRITEO: {
        scenario: "SPARSE",
        cpm: 6500, ctr: 0.017, atcRate: 0.3,
        organicDailyBase: 58000, organicCvr: 0.035, organicAov: 42000,
        zeroWeekCount: 4, weeklyCostBase: 420000,
      },
    },
    PROMO_JULY: promoOrganic("aurora-beauty", { META: 1.5, TIKTOK: 1.6, GOOGLE: 1.2, NAVER: 1.1, CRITEO: 1.1 }),
    PROMO_AUG: promoOrganic("aurora-beauty", { META: 1.4, TIKTOK: 1.5, GOOGLE: 1.15, NAVER: 1.05, CRITEO: 1.05 }),
  },

  "urban-fit": {
    ALWAYS: {
      META: { scenario: "ORGANIC", cpm: 7200, ctr: 0.02, atcRate: 0.33, cvr: 0.04, aov: 44000, dailyBase: 68000 },
      GOOGLE: { scenario: "ORGANIC", cpm: 8000, ctr: 0.024, atcRate: 0.32, cvr: 0.042, aov: 48000, dailyBase: 75000 },
      TIKTOK: { scenario: "ORGANIC", cpm: 6200, ctr: 0.017, atcRate: 0.28, cvr: 0.03, aov: 36000, dailyBase: 35000 },
      NAVER: { scenario: "ORGANIC", cpm: 5800, ctr: 0.014, atcRate: 0.25, cvr: 0.028, aov: 39000, dailyBase: 30000 },
      CRITEO: { scenario: "ORGANIC", cpm: 6800, ctr: 0.015, atcRate: 0.26, cvr: 0.027, aov: 41000, dailyBase: 28000 },
    },
    PROMO_JULY: promoOrganic("urban-fit", { META: 1.3, GOOGLE: 1.35, TIKTOK: 1.2, NAVER: 1.1, CRITEO: 1.1 }),
    PROMO_AUG: promoOrganic("urban-fit", { META: 1.25, GOOGLE: 1.3, TIKTOK: 1.15, NAVER: 1.05, CRITEO: 1.05 }),
  },

  "living-lab": {
    ALWAYS: {
      NAVER: { scenario: "ORGANIC", cpm: 5200, ctr: 0.016, atcRate: 0.3, cvr: 0.04, aov: 37000, dailyBase: 62000 },
      CRITEO: { scenario: "ORGANIC", cpm: 6000, ctr: 0.017, atcRate: 0.29, cvr: 0.038, aov: 39000, dailyBase: 58000 },
      META: {
        scenario: "LOW_PURCHASE",
        cpm: 7500, ctr: 0.019, atcRate: 0.3, aov: 41000, dailyBase: 52000, targetTotalPurchases: 12,
      },
      TIKTOK: { scenario: "ORGANIC", cpm: 6300, ctr: 0.012, atcRate: 0.2, cvr: 0.018, aov: 30000, dailyBase: 3800 },
      GOOGLE: {
        scenario: "ACTIVE_ONLY",
        cpm: 7800, ctr: 0.018, atcRate: 0.28, cvr: 0.055, aov: 45000, dailyBase: 260000,
        activeDates: ["2026-05-12", "2026-06-03", "2026-06-24", "2026-07-15", "2026-08-05"],
      },
    },
    PROMO_JULY: {
      NAVER: { scenario: "ORGANIC", cpm: 5200, ctr: 0.016, atcRate: 0.3, cvr: 0.04, aov: 37000, dailyBase: 90000 },
      CRITEO: { scenario: "ORGANIC", cpm: 6000, ctr: 0.017, atcRate: 0.29, cvr: 0.038, aov: 39000, dailyBase: 85000 },
      META: { scenario: "ORGANIC", cpm: 7500, ctr: 0.019, atcRate: 0.3, cvr: 0.032, aov: 41000, dailyBase: 60000 },
      TIKTOK: { scenario: "ORGANIC", cpm: 6300, ctr: 0.017, atcRate: 0.25, cvr: 0.028, aov: 33000, dailyBase: 55000 },
      GOOGLE: { scenario: "ORGANIC", cpm: 7800, ctr: 0.018, atcRate: 0.27, cvr: 0.03, aov: 43000, dailyBase: 58000 },
    },
    PROMO_AUG: {
      NAVER: { scenario: "ORGANIC", cpm: 5200, ctr: 0.016, atcRate: 0.3, cvr: 0.04, aov: 37000, dailyBase: 88000 },
      CRITEO: { scenario: "ORGANIC", cpm: 6000, ctr: 0.017, atcRate: 0.29, cvr: 0.038, aov: 39000, dailyBase: 82000 },
      META: { scenario: "ORGANIC", cpm: 7500, ctr: 0.019, atcRate: 0.3, cvr: 0.032, aov: 41000, dailyBase: 56000 },
      GOOGLE: { scenario: "ORGANIC", cpm: 7800, ctr: 0.018, atcRate: 0.27, cvr: 0.03, aov: 43000, dailyBase: 54000 },
      TIKTOK: { scenario: "ZERO_PURCHASE", cpm: 6300, ctr: 0.017, atcRate: 0.05, aov: 0, dailyBase: 45000 },
    },
  },
};

export function mediaPlanFor(advertiserId, projectType, media) {
  return MEDIA_PLANS[advertiserId]?.[projectType]?.[media] ?? null;
}
