import { dateRange, dayOfWeek, monthOf, isWeekend } from "./dates.mjs";
import { rngFor, uniform, gaussian, distributeIntegerBySum } from "./rng.mjs";

function clampMin(v, min) {
  return v < min ? min : v;
}

function weekdayFactor(iso) {
  // 평일 광고비가 더 높고(요청 8번 예시), 주말은 CVR이 오르는 대신 광고비는 소폭 낮게.
  return isWeekend(iso) ? 0.82 : 1.06;
}

function weekendCvrBoost(iso) {
  return isWeekend(iso) ? 1.12 : 1.0;
}

function monthTrendFactor(iso, monthTrend) {
  const m = monthOf(iso);
  return monthTrend[m] ?? 1.0;
}

/**
 * 하루치 impressions/clicks/add_to_cart/purchases/revenue를 그 날의 cost로부터 계산한다.
 * (impressions/clicks는 CPM/CTR로 비용에 거의 비례하게, 구매는 CVR + 완만한 diminishing
 * returns로 "무조건 선형은 아니게" 만든다 — 요청 1/7번.)
 */
function dailyFunnelFromCost(rng, cost, params, typicalDailyCost) {
  const cpmJitter = uniform(rng, 0.94, 1.06);
  const impressions = Math.round((cost * 1000) / (params.cpm * cpmJitter));

  const ctrJitter = uniform(rng, 0.9, 1.1);
  const clicks = Math.max(0, Math.round(impressions * params.ctr * ctrJitter));

  const addToCart = Math.max(0, Math.round(clicks * params.atcRate * uniform(rng, 0.85, 1.15)));

  // 전형적 일일 예산보다 훨씬 큰 지출을 한 날은 한계 효율이 떨어지도록 완만한 saturation을 준다.
  const spikeRatio = typicalDailyCost > 0 ? cost / typicalDailyCost : 1;
  const saturation = 1 / (1 + Math.max(0, spikeRatio - 1) * 0.35);
  const cvrJitter = uniform(rng, 0.85, 1.15);
  const purchases = Math.max(0, Math.round(clicks * params.cvr * saturation * cvrJitter));

  const aovJitter = uniform(rng, 0.9, 1.1);
  const revenue = Math.max(0, Math.round(purchases * params.aov * aovJitter));

  return { impressions, clicks, addToCart, purchases, revenue };
}

/**
 * 요청 1/8번: 광고비 증가↔성과 증가의 자연스러운 관계 + 요일 효과 + 월별 트렌드(시즌성) +
 * 노이즈를 가진 "organic" 일별 시리즈. 대부분의 캠페인에 사용한다.
 */
export function generateOrganicDays(seed, key, dates, params) {
  const rng = rngFor(seed, key);
  const monthTrend = params.monthTrend ?? {};
  const rows = [];
  for (const iso of dates) {
    const dailyNoise = 1 + gaussian(rng) * (params.noiseRatio ?? 0.12);
    const cost = Math.max(
      0,
      Math.round(params.dailyBase * weekdayFactor(iso) * monthTrendFactor(iso, monthTrend) * dailyNoise),
    );
    const funnel = dailyFunnelFromCost(rng, cost, { ...params, cvr: params.cvr * weekendCvrBoost(iso) }, params.dailyBase);
    rows.push({ date: iso, cost, ...funnel });
  }
  return rows;
}

/**
 * SimulationService의 valid-week 판정을 그대로 만족하도록(cost/impr/clicks>0, purchases>0,
 * revenue>=0) 유지하되 주간 cost를 거의 고정해(변동폭 <1.2배) 모델을 의도적으로 무효화한다.
 */
export function generateFlatWeeklyDays(seed, key, weeks, params) {
  const rng = rngFor(seed, key);
  const rows = [];
  for (const week of weeks) {
    const weeklyCost = Math.round(params.weeklyCostBase * uniform(rng, 0.97, 1.03));
    const perDay = distributeIntegerBySum(week.map(() => 1), weeklyCost);
    for (let i = 0; i < week.length; i++) {
      const cost = Math.max(1, perDay[i]);
      const funnel = dailyFunnelFromCost(rng, cost, params, params.weeklyCostBase / 7);
      rows.push({ date: week[i], cost, ...funnel });
    }
  }
  return rows;
}

/**
 * CRITEO 시나리오: 후보 8주 중 앞쪽 몇 주는 cost=0(무효 주차)으로 비워 valid week 수를
 * 6주 미만으로 떨어뜨리고, 나머지 주는 정상 데이터를 채운다("최근 데이터가 부족해진" 매체).
 */
export function generateSparseWeeklyDays(seed, key, weeks, params, zeroWeekCount) {
  const rng = rngFor(seed, key);
  const rows = [];
  weeks.forEach((week, idx) => {
    if (idx < zeroWeekCount) {
      for (const iso of week) {
        rows.push({ date: iso, cost: 0, impressions: 0, clicks: 0, addToCart: 0, purchases: 0, revenue: 0 });
      }
      return;
    }
    const weeklyCost = Math.round(params.weeklyCostBase * uniform(rng, 0.9, 1.1));
    const perDay = distributeIntegerBySum(week.map(() => 1), weeklyCost);
    for (let i = 0; i < week.length; i++) {
      const cost = Math.max(1, perDay[i]);
      const funnel = dailyFunnelFromCost(rng, cost, params, params.weeklyCostBase / 7);
      rows.push({ date: week[i], cost, ...funnel });
    }
  });
  return rows;
}

/**
 * PRD 10.4의 y = a*ln(x) + b 관계를 실제로 만족하는 8주치 주간 cost/purchases/revenue를
 * 만든 뒤 일별로 쪼갠다. R²가 확실히 0.75 이상 나오도록 노이즈를 작게 준다.
 * weeklyCosts는 최대/최소 비율이 SIM_HIGH_COST_RATIO(1.3) 이상이 되도록 미리 설계해서 받는다.
 */
export function generateLogCurveWeeklyDays(seed, key, weeks, weeklyCosts, model, params) {
  const rng = rngFor(seed, key);
  const rows = [];
  weeks.forEach((week, idx) => {
    const weeklyCost = weeklyCosts[idx];
    const purchaseNoise = 1 + gaussian(rng) * 0.03;
    const revenueNoise = 1 + gaussian(rng) * 0.03;
    const weeklyPurchases = Math.max(
      1,
      Math.round((model.aPurchase * Math.log(weeklyCost) + model.bPurchase) * purchaseNoise),
    );
    const weeklyRevenue = Math.max(
      0,
      Math.round((model.aRevenue * Math.log(weeklyCost) + model.bRevenue) * revenueNoise),
    );

    const costPerDay = distributeIntegerBySum(week.map(() => 1), weeklyCost);
    const purchasesPerDay = distributeIntegerBySum(week.map((iso) => (isWeekend(iso) ? 1.15 : 1)), weeklyPurchases);
    const revenuePerDay = distributeIntegerBySum(purchasesPerDay.map((p) => clampMin(p, 0.001)), weeklyRevenue);

    for (let i = 0; i < week.length; i++) {
      const cost = Math.max(1, costPerDay[i]);
      const purchases = purchasesPerDay[i];
      const revenue = revenuePerDay[i];
      const cpmJitter = uniform(rng, 0.95, 1.05);
      const impressions = Math.round((cost * 1000) / (params.cpm * cpmJitter));
      const clicks = Math.max(purchases, Math.round(impressions * params.ctr * uniform(rng, 0.95, 1.05)));
      const addToCart = Math.max(purchases, Math.round(clicks * params.atcRate));
      rows.push({ date: week[i], cost, impressions, clicks, addToCart, purchases, revenue });
    }
  });
  return rows;
}

/** 지정한 날짜에만 활동하고 나머지는 완전히 비활동(행 없음)인 시리즈 — 운영일 미달 케이스용. */
export function generateActiveOnlyDays(seed, key, activeDates, params) {
  const rng = rngFor(seed, key);
  const rows = [];
  for (const iso of activeDates) {
    const cost = Math.max(1, Math.round(params.dailyBase * uniform(rng, 0.9, 1.1)));
    const funnel = dailyFunnelFromCost(rng, cost, params, params.dailyBase);
    rows.push({ date: iso, cost, ...funnel });
  }
  return rows;
}

/** 전체 기간 동안 cost/impressions/clicks는 정상이지만 구매가 정확히 0으로 고정된 시리즈. */
export function generateZeroPurchaseDays(seed, key, dates, params) {
  const rng = rngFor(seed, key);
  const rows = [];
  for (const iso of dates) {
    const cost = Math.max(1, Math.round(params.dailyBase * weekdayFactor(iso) * uniform(rng, 0.92, 1.08)));
    const cpmJitter = uniform(rng, 0.95, 1.05);
    const impressions = Math.round((cost * 1000) / (params.cpm * cpmJitter));
    const clicks = Math.max(0, Math.round(impressions * params.ctr));
    const addToCart = Math.max(0, Math.round(clicks * params.atcRate * 0.3));
    rows.push({ date: iso, cost, impressions, clicks, addToCart, purchases: 0, revenue: 0 });
  }
  return rows;
}

/** 낮은 구매 전환(내부 필터 후 10 미만)이 되도록 총 raw purchases를 정확한 목표치로 고정. */
export function generateLowPurchaseDays(seed, key, dates, params, targetTotalPurchases) {
  const rng = rngFor(seed, key);
  const costRows = dates.map((iso) => {
    const cost = Math.max(1, Math.round(params.dailyBase * weekdayFactor(iso) * (1 + gaussian(rng) * 0.1)));
    return { date: iso, cost };
  });
  const weights = costRows.map((r) => r.cost);
  const purchasesPerDay = distributeIntegerBySum(weights, targetTotalPurchases);
  return costRows.map((r, i) => {
    const cpmJitter = uniform(rng, 0.95, 1.05);
    const impressions = Math.round((r.cost * 1000) / (params.cpm * cpmJitter));
    const clicks = Math.max(0, Math.round(impressions * params.ctr));
    const addToCart = Math.max(purchasesPerDay[i], Math.round(clicks * params.atcRate * 0.4));
    const revenue = Math.round(purchasesPerDay[i] * params.aov * uniform(rng, 0.9, 1.1));
    return { date: r.date, cost: r.cost, impressions, clicks, addToCart, purchases: purchasesPerDay[i], revenue };
  });
}

export { dateRange, dayOfWeek };
