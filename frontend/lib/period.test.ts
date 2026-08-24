import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { computeRange, toISODate } from "./period";

describe("computeRange", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-24T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("AC-01: '30d' 기본값은 오늘을 포함해 정확히 30일 폭이다", () => {
    const { from, to } = computeRange("30d", "", "");
    expect(to).toBe("2026-08-24");
    expect(from).toBe("2026-07-26");

    const days = (Date.parse(to) - Date.parse(from)) / (1000 * 60 * 60 * 24) + 1;
    expect(days).toBe(30);
  });

  it("'7d'는 오늘을 포함해 정확히 7일 폭이다", () => {
    const { from, to } = computeRange("7d", "", "");
    expect(to).toBe("2026-08-24");
    expect(from).toBe("2026-08-18");
  });

  it("'thisMonth'는 이번 달 1일부터 오늘까지다", () => {
    const { from, to } = computeRange("thisMonth", "", "");
    expect(from).toBe("2026-08-01");
    expect(to).toBe("2026-08-24");
  });

  it("'lastMonth'는 지난 달 1일부터 말일까지다", () => {
    const { from, to } = computeRange("lastMonth", "", "");
    expect(from).toBe("2026-07-01");
    expect(to).toBe("2026-07-31");
  });

  it("'custom'은 입력한 날짜를 그대로 사용한다", () => {
    const { from, to } = computeRange("custom", "2026-01-01", "2026-01-10");
    expect(from).toBe("2026-01-01");
    expect(to).toBe("2026-01-10");
  });
});

describe("toISODate", () => {
  it("Date를 로컬 날짜 기준 yyyy-MM-dd 문자열로 변환한다", () => {
    // 자정 근처가 아닌 시각을 써서 로컬 타임존과 무관하게 같은 달력일로 떨어지게 한다.
    expect(toISODate(new Date("2026-03-05T03:00:00Z"))).toBe("2026-03-05");
  });
});
