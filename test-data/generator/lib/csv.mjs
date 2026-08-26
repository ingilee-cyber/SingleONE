// frontend/e2e/testData.ts의 buildPerformanceCsv와 동일한 컬럼 순서를 그대로 따른다.
export const PERFORMANCE_HEADER = [
  "date", "advertiser_id", "advertiser_name", "media", "campaign_id", "campaign_name",
  "ad_group_id", "ad_group_name", "ad_id", "ad_name",
  "impressions", "clicks", "cost", "add_to_cart", "purchases", "purchase_revenue",
];

// frontend/e2e/journey.spec.ts의 실제 업로드 CSV와 동일한 컬럼 순서.
export const JOURNEY_HEADER = [
  "event_id", "anonymous_user_id", "advertiser_id", "event_type", "event_timestamp",
  "media", "campaign_id", "ad_group_id", "ad_id", "order_id", "purchase_revenue",
];

function csvEscape(value) {
  const s = value === undefined || value === null ? "" : String(value);
  if (/[",\n]/.test(s)) {
    return `"${s.replace(/"/g, '""')}"`;
  }
  return s;
}

export function rowsToCsv(header, rows) {
  const lines = [header.join(",")];
  for (const row of rows) {
    lines.push(header.map((key) => csvEscape(row[key])).join(","));
  }
  return lines.join("\n") + "\n";
}

/** 우리 generator가 직접 만든 CSV만 읽는 용도(임베디드 콤마/줄바꿈이 없다고 가정한 단순 파서). */
export function parseCsv(text) {
  const lines = text.split("\n").filter((l) => l.length > 0);
  const header = lines[0].split(",");
  return lines.slice(1).map((line) => {
    const cols = line.split(",");
    const row = {};
    header.forEach((h, i) => (row[h] = cols[i] ?? ""));
    return row;
  });
}
