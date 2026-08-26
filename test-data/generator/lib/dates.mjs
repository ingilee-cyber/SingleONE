// 전부 UTC 기준 달력 계산만 사용한다(시:분:초 없이 날짜만 다루므로 타임존 이슈가 없다).
// Performance date는 순수 날짜 문자열이고, Journey timestamp만 별도로 시:분을 붙인다.

export function toISODate(date) {
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, "0");
  const d = String(date.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function parseISODate(s) {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

export function addDays(date, n) {
  const d = new Date(date.getTime());
  d.setUTCDate(d.getUTCDate() + n);
  return d;
}

/** start~end(둘 다 포함) 사이의 날짜 문자열 배열. */
export function dateRange(startISO, endISO) {
  const start = parseISODate(startISO);
  const end = parseISODate(endISO);
  const days = [];
  for (let d = start; d.getTime() <= end.getTime(); d = addDays(d, 1)) {
    days.push(toISODate(d));
  }
  return days;
}

export function diffDaysInclusive(startISO, endISO) {
  return dateRange(startISO, endISO).length;
}

/** 0=일요일 ... 6=토요일 (UTC 기준, 날짜만 다루므로 지역 시간대 영향 없음). */
export function dayOfWeek(iso) {
  return parseISODate(iso).getUTCDay();
}

export function isWeekend(iso) {
  const dow = dayOfWeek(iso);
  return dow === 0 || dow === 6;
}

/** 1=1월 ... 12=12월. */
export function monthOf(iso) {
  return parseISODate(iso).getUTCMonth() + 1;
}

/** startISO를 포함하는 주의 월요일부터 7일씩 묶어 [{weekStart, days:[...]}] 형태로 나눈다. */
export function chunkIntoWeeks(startISO, endISO) {
  const days = dateRange(startISO, endISO);
  const weeks = [];
  for (let i = 0; i < days.length; i += 7) {
    weeks.push(days.slice(i, i + 7));
  }
  return weeks;
}

/**
 * SimulationService.java와 정확히 동일한 방식(weekEnd = baseTo - 7*i, weekStart = weekEnd-6,
 * i=0..count-1)으로 baseTo를 기준으로 거꾸로 7일씩 n개 주를 나눈다. 과거→최신 순서로 반환한다.
 * Media Planning Simulation의 "기준 성과 기간" 종료일을 endISO로 맞춰야 실제 후보 8주 window와
 * 정확히 일치한다.
 */
export function lastNWeeksEndingAt(endISO, count) {
  const weeks = [];
  const end = parseISODate(endISO);
  for (let i = count - 1; i >= 0; i--) {
    const weekEnd = addDays(end, -7 * i);
    const weekStart = addDays(weekEnd, -6);
    weeks.push(dateRange(toISODate(weekStart), toISODate(weekEnd)));
  }
  return weeks;
}

/** Journey timestamp 문자열(UTC, 초 단위, PRD 11.1 UTC 저장 규칙과 무관하게 업로드 파일 자체는
 * Z 오프셋을 명시해 타임존 해석 모호성을 없앤다 — JourneyRowParser가 Instant.parse로 그대로 받는다). */
export function toTimestamp(iso, hour, minute = 0, second = 0) {
  const d = parseISODate(iso);
  d.setUTCHours(hour, minute, second, 0);
  return d.toISOString().replace(/\.\d{3}Z$/, "Z");
}

export function addHoursToTimestamp(isoTimestamp, hours) {
  const d = new Date(isoTimestamp);
  d.setTime(d.getTime() + hours * 3600 * 1000);
  return d.toISOString().replace(/\.\d{3}Z$/, "Z");
}
