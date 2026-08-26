import type { APIRequestContext, Page } from "@playwright/test";

export const BACKEND_URL = "http://localhost:8080";

/**
 * Global Header의 광고주 Autocomplete에서 특정 광고주를 선택한다. 페이지는 이미
 * `page.goto(...)`로 진입해 AppShell이 마운트되고 광고주 목록을 불러온 상태여야 한다.
 * (전역 광고주 선택 도입 후 각 화면의 "광고주 ID" 텍스트박스는 데이터 관리 화면을 제외하고
 * 전부 제거됐다.)
 */
export async function selectAdvertiser(page: Page, advertiserId: string) {
  const input = page.getByRole("combobox", { name: "광고주" });
  await input.click();
  await input.fill(advertiserId);
  await page.getByRole("option", { name: advertiserId, exact: true }).click();
}

export async function uploadPerformanceCsv(request: APIRequestContext, advertiserId: string, csv: string, filename = "perf.csv") {
  const res = await request.post(`${BACKEND_URL}/api/v1/uploads/performance`, {
    multipart: { advertiserId, file: { name: filename, mimeType: "text/csv", buffer: Buffer.from(csv, "utf-8") } },
  });
  return res.json() as Promise<{ uploadBatchId: number }>;
}

export async function uploadJourneyCsv(request: APIRequestContext, advertiserId: string, csv: string, filename = "journey.csv") {
  const res = await request.post(`${BACKEND_URL}/api/v1/uploads/journey`, {
    multipart: { advertiserId, file: { name: filename, mimeType: "text/csv", buffer: Buffer.from(csv, "utf-8") } },
  });
  return res.json() as Promise<{ uploadBatchId: number }>;
}

export async function waitForUploadStatus(request: APIRequestContext, batchId: number, timeoutMs = 20000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const res = await request.get(`${BACKEND_URL}/api/v1/uploads/${batchId}`);
    const body = await res.json();
    if (body.status !== "VALIDATING" && body.status !== "IMPORTING") {
      return body;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`업로드 상태가 시간 내에 확정되지 않음(batchId=${batchId})`);
}

export async function createProject(
  request: APIRequestContext,
  advertiserId: string,
  projectName: string,
  campaigns: { media: string; campaignId: string }[],
) {
  const res = await request.post(`${BACKEND_URL}/api/v1/advertisers/${advertiserId}/projects`, {
    data: { projectName, campaigns },
  });
  return res.json() as Promise<{ projectId: number }>;
}

export async function deleteAdvertiserData(request: APIRequestContext, advertiserId: string) {
  // 테스트 전용 정리용 - 실제 API가 없어 개별 삭제 대신 다음 실행에서 겹치지 않도록 매 테스트가
  // 고유한 advertiserId(타임스탬프 포함)를 쓰는 방식으로 충돌을 피한다. 이 함수는 향후 정리
  // API가 생기면 채울 자리표시자로 남겨둔다.
  void request;
  void advertiserId;
}

function performanceCsvRow(fields: Record<string, string | number>) {
  const header = [
    "date",
    "advertiser_id",
    "advertiser_name",
    "media",
    "campaign_id",
    "campaign_name",
    "ad_group_id",
    "ad_group_name",
    "ad_id",
    "ad_name",
    "impressions",
    "clicks",
    "cost",
    "add_to_cart",
    "purchases",
    "purchase_revenue",
  ];
  return header.map((key) => fields[key]).join(",");
}

export function buildPerformanceCsv(rows: Record<string, string | number>[]) {
  const header =
    "date,advertiser_id,advertiser_name,media,campaign_id,campaign_name,ad_group_id,ad_group_name,ad_id,ad_name,impressions,clicks,cost,add_to_cart,purchases,purchase_revenue";
  return [header, ...rows.map(performanceCsvRow)].join("\n") + "\n";
}
