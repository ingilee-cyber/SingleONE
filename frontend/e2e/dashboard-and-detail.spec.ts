import { expect, test } from "@playwright/test";
import { buildPerformanceCsv, createProject, uploadPerformanceCsv, waitForUploadStatus } from "./testData";

/**
 * PRD 6장/7장 골든 패스: 업로드 -> 프로젝트 생성 -> Dashboard 기본 진입(AC-01) -> 매체 클릭 ->
 * 계층 상세 탐색 -> Breadcrumb으로 Dashboard 복귀 시 필터 유지(AC-23/24).
 */
test.describe("Dashboard & 상세 계층 골든 패스", () => {
  test("Dashboard 기본 진입, 매체 상세/캠페인 상세 탐색, Breadcrumb으로 Dashboard 복귀 시 컨텍스트 유지", async ({ page, request }) => {
    const advertiserId = `e2e-dash-${Date.now()}`;
    const today = new Date();
    const rows = [];
    for (let i = 0; i < 10; i++) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      const date = d.toISOString().slice(0, 10);
      rows.push({
        date, advertiser_id: advertiserId, advertiser_name: "E2E광고주", media: "META", campaign_id: "c1",
        campaign_name: "메타캠페인", ad_group_id: "ag-1", ad_group_name: "메타광고그룹1", ad_id: "ad-1", ad_name: "메타광고1",
        impressions: 50000, clicks: 1000, cost: 300000, add_to_cart: 0, purchases: 10, purchase_revenue: 600000,
      });
      // GOOGLE은 총 Cost를 1,000,000 미만으로 남겨 일부러 "데이터 부족"(INSUFFICIENT_DATA)으로
      // 만든다. MediaIndexChart는 VALID 매체는 ECharts 캔버스 막대(접근성 트리에 없음)로만
      // 클릭 가능하고, VALID가 아닌 매체만 DOM Chip으로 렌더링돼 Playwright로 안정적으로
      // 클릭할 수 있기 때문이다(app/dashboard/MediaIndexChart.tsx).
      rows.push({
        date, advertiser_id: advertiserId, advertiser_name: "E2E광고주", media: "GOOGLE", campaign_id: "c1",
        campaign_name: "구글캠페인", ad_group_id: "ag-1", ad_group_name: "구글광고그룹1", ad_id: "ad-1", ad_name: "구글광고1",
        impressions: 6000, clicks: 120, cost: 50000, add_to_cart: 0, purchases: 1, purchase_revenue: 70000,
      });
    }
    const csv = buildPerformanceCsv(rows);
    const { uploadBatchId } = await uploadPerformanceCsv(request, advertiserId, csv);
    const uploadResult = await waitForUploadStatus(request, uploadBatchId);
    expect(uploadResult.status).toBe("SUCCESS");

    const { projectId } = await createProject(request, advertiserId, "E2E대시보드프로젝트", [
      { media: "META", campaignId: "c1" },
      { media: "GOOGLE", campaignId: "c1" },
    ]);
    expect(projectId).toBeGreaterThan(0);

    await page.goto("/dashboard", { timeout: 60000 });
    await page.getByRole("textbox", { name: "광고주 ID" }).fill(advertiserId);

    // AC-01: 기본 기간은 최근 30일, 이전 기간 비교는 ON이어야 한다.
    await expect(page.getByRole("button", { name: "최근 30일" })).toHaveAttribute("aria-pressed", "true", { timeout: 15000 });
    await expect(page.getByRole("switch", { name: "이전 기간 비교" })).toBeChecked();

    await page.getByTestId("kpi-cards").waitFor({ timeout: 45000 });

    // 매체 Chip("GOOGLE: 데이터 부족") 클릭 -> 매체 상세(AC-24).
    await page.getByText(/^GOOGLE:/).click();
    await page.getByRole("heading", { name: "매체 상세: GOOGLE" }).waitFor({ timeout: 45000 });

    // 캠페인 목록에서 캠페인 클릭 -> 캠페인 상세.
    await page.getByText("구글캠페인").click();
    await page.getByRole("heading", { name: "캠페인 상세: 구글캠페인" }).waitFor({ timeout: 45000 });

    // Breadcrumb으로 매체 상세 -> Dashboard까지 복귀.
    await page.getByRole("link", { name: "매체: GOOGLE" }).click();
    await page.getByRole("heading", { name: "매체 상세: GOOGLE" }).waitFor({ timeout: 45000 });
    await page.getByRole("link", { name: "Dashboard" }).click();

    // AC-23: Dashboard로 돌아왔을 때 광고주/프로젝트/기간 상태가 유지돼야 한다.
    await page.getByTestId("kpi-cards").waitFor({ timeout: 45000 });
    await expect(page.getByRole("textbox", { name: "광고주 ID" })).toHaveValue(advertiserId);
  });
});
