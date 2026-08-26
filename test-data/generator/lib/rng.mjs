// 고정 seed 기반 결정론적 PRNG(mulberry32). 외부 라이브러리 없이 순수 구현하며,
// 동일 seed로 여러 번 호출해도 항상 같은 순서의 값을 낸다(요청 1번: deterministic).
export function makeRng(seed) {
  let state = seed >>> 0;
  return function next() {
    state |= 0;
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** 여러 독립적인 하위 스트림이 필요할 때, 문자열 키로부터 안정적인 하위 seed를 만든다. */
export function deriveSeed(baseSeed, key) {
  let hash = baseSeed >>> 0;
  for (let i = 0; i < key.length; i++) {
    hash = (Math.imul(hash ^ key.charCodeAt(i), 2654435761) >>> 0);
  }
  return hash >>> 0;
}

export function rngFor(baseSeed, key) {
  return makeRng(deriveSeed(baseSeed, key));
}

/** [min, max] 범위의 실수. */
export function uniform(rng, min, max) {
  return min + rng() * (max - min);
}

/** 평균 0, 표준편차 1에 가까운 근사 정규분포(Box-Muller). */
export function gaussian(rng) {
  const u1 = Math.max(rng(), 1e-9);
  const u2 = rng();
  return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
}

/** 정수 배열에서 합계를 정확히 보존하며 비례 배분(최대 나머지법). */
export function distributeIntegerBySum(weights, total) {
  if (total <= 0 || weights.length === 0) {
    return weights.map(() => 0);
  }
  const weightSum = weights.reduce((a, b) => a + b, 0);
  if (weightSum <= 0) {
    const base = Math.floor(total / weights.length);
    const result = weights.map(() => base);
    let remainder = total - base * weights.length;
    for (let i = 0; i < result.length && remainder > 0; i++, remainder--) {
      result[i] += 1;
    }
    return result;
  }
  const raw = weights.map((w) => (w / weightSum) * total);
  const floors = raw.map(Math.floor);
  let allocated = floors.reduce((a, b) => a + b, 0);
  let remainder = total - allocated;
  const order = raw
    .map((v, i) => ({ i, frac: v - floors[i] }))
    .sort((a, b) => b.frac - a.frac);
  const result = [...floors];
  for (let k = 0; k < remainder; k++) {
    result[order[k % order.length].i] += 1;
  }
  return result;
}
