export type PeriodPreset = "7d" | "30d" | "thisMonth" | "lastMonth" | "custom";

export const PERIOD_OPTIONS: { value: PeriodPreset; label: string }[] = [
  { value: "7d", label: "최근 7일" },
  { value: "30d", label: "최근 30일" },
  { value: "thisMonth", label: "이번 달" },
  { value: "lastMonth", label: "지난 달" },
  { value: "custom", label: "직접 설정" },
];

// CLAUDE.md Hard Rule 14: 화면 표시는 로컬(Asia/Seoul) 날짜 기준이어야 한다. Date.toISOString()은
// UTC로 변환하므로, UTC+9 등 UTC보다 앞선 시간대에서는 자정 근처 시각이나 new Date(y,m,1) 같은
// 로컬 자정 기준 값이 하루 전 날짜로 어긋난다. 반드시 로컬 연/월/일 getter로 문자열을 만든다.
export function toISODate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function computeRange(preset: PeriodPreset, customFrom: string, customTo: string): { from: string; to: string } {
  const today = new Date();
  if (preset === "custom") {
    return { from: customFrom, to: customTo };
  }
  if (preset === "7d") {
    const from = new Date(today);
    from.setDate(from.getDate() - 6);
    return { from: toISODate(from), to: toISODate(today) };
  }
  if (preset === "thisMonth") {
    const from = new Date(today.getFullYear(), today.getMonth(), 1);
    return { from: toISODate(from), to: toISODate(today) };
  }
  if (preset === "lastMonth") {
    const from = new Date(today.getFullYear(), today.getMonth() - 1, 1);
    const to = new Date(today.getFullYear(), today.getMonth(), 0);
    return { from: toISODate(from), to: toISODate(to) };
  }
  const from = new Date(today);
  from.setDate(from.getDate() - 29);
  return { from: toISODate(from), to: toISODate(today) };
}
