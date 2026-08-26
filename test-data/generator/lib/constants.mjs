// PRD/코드로 확인한 값들. 필터율은 "예상 결과가 어떻게 나올지 미리 계산"하는 용도로만 이
// 생성기 내부에서 쓰고, 어떤 산출물(CSV/JSON/README)에도 그대로 적지 않는다(Hard Rule 7).
export const SEED = 20260826;

export const START_DATE = "2026-05-01";
export const END_DATE = "2026-08-25"; // 117일, "오늘"(2026-08-26) 이전까지만

export const PROMO_JULY_START = "2026-07-01";
export const PROMO_JULY_END = "2026-07-31";
export const PROMO_AUG_START = "2026-08-01";
export const PROMO_AUG_END = "2026-08-25";

export const MEDIA_LIST = ["META", "TIKTOK", "GOOGLE", "NAVER", "CRITEO"];

// docs/SingleONE_PRD.md 8.3 + V5__seed_internal_media_filter.sql — 내부 전용, 산출물에 노출 금지.
export const INTERNAL_FILTER_RATE = {
  META: 0.65,
  TIKTOK: 0.62,
  GOOGLE: 0.69,
  NAVER: 0.64,
  CRITEO: 0.61,
};

// SingleOneIndexCalculator 정확한 상수.
export const INDEX_MIN_COST = 1_000_000;
export const INDEX_MIN_PURCHASES = 10; // SingleONE 필터 적용 후 내부 소수값 기준
export const INDEX_MIN_OPERATING_DAYS = 7;

// SimulationService 정확한 상수.
export const SIM_CANDIDATE_WEEKS = 8;
export const SIM_MIN_VALID_WEEKS = 6;
export const SIM_MIN_PURCHASE_SUM = 100;
export const SIM_MIN_COST_RATIO = 1.2;
export const SIM_HIGH_COST_RATIO = 1.3;
export const SIM_HIGH_R_SQUARED = 0.75;
export const SIM_MODEL_MIN_R_SQUARED = 0.5;

export const PROJECT_TYPES = ["ALWAYS", "PROMO_JULY", "PROMO_AUG"];

export const PROJECT_TYPE_LABEL = {
  ALWAYS: "상시",
  PROMO_JULY: "프로모션(7월)",
  PROMO_AUG: "프로모션(8월)",
};

export const PROJECT_TYPE_CODE = {
  ALWAYS: "ALW",
  PROMO_JULY: "P07",
  PROMO_AUG: "P08",
};

export const PROJECT_TYPE_RANGE = {
  ALWAYS: [START_DATE, END_DATE],
  PROMO_JULY: [PROMO_JULY_START, PROMO_JULY_END],
  PROMO_AUG: [PROMO_AUG_START, PROMO_AUG_END],
};
