import { expect, test } from "@playwright/test";
import { buildPerformanceCsv, createProject, selectAdvertiser, uploadPerformanceCsv, waitForUploadStatus } from "./testData";

/**
 * PRD 10장 골든 패스: 8주치 성과 업로드 -> 프로젝트 생성 -> 시뮬레이션 실행.
 * AC-43(추천/최적화 표현 금지, 실행 전/후 모두)과 AC-53(새로고침 시 초기화)을 실제 브라우저로 확인한다.
 */
test.describe("Media Planning Simulation 골든 패스", () => {
  test("AC-43: 추천/최적화 표현이 실행 전후 모두 없고, AC-53: 새로고침하면 입력이 초기화된다", async ({ page, request }) => {
    const advertiserId = `e2e-sim-${Date.now()}`;
    const baseTo = new Date();
    const weeklyCost = [700000, 980000, 1260000, 1540000, 1820000, 2100000, 2380000, 2660000];
    const weeklyPurchases = [62, 85, 100, 111, 120, 128, 134, 139];
    const weeklyRevenue = [1230769, 1692308, 2000000, 2215385, 2400000, 2553846, 2676923, 2769231];
    const rows = [];
    for (let i = 0; i < 8; i++) {
      const d = new Date(baseTo);
      d.setDate(d.getDate() - 7 * (7 - i));
      const date = d.toISOString().slice(0, 10);
      rows.push({
        date, advertiser_id: advertiserId, advertiser_name: "E2E광고주", media: "META", campaign_id: "c1",
        campaign_name: "메타캠페인", ad_group_id: "ag-1", ad_group_name: "광고그룹1", ad_id: "ad-1", ad_name: "광고1",
        impressions: 500000, clicks: 10000, cost: weeklyCost[i], add_to_cart: 0,
        purchases: weeklyPurchases[i], purchase_revenue: weeklyRevenue[i],
      });
    }
    // AC-17: 프로젝트는 서로 다른 매체가 최소 2개 있어야 저장할 수 있으므로, 시뮬레이션에는
    // 쓰지 않지만 GOOGLE 캠페인도 하나 곁들인다.
    rows.push({
      date: rows[rows.length - 1].date, advertiser_id: advertiserId, advertiser_name: "E2E광고주", media: "GOOGLE",
      campaign_id: "c1", campaign_name: "구글캠페인", ad_group_id: "ag-1", ad_group_name: "광고그룹1", ad_id: "ad-1",
      ad_name: "광고1", impressions: 1000, clicks: 100, cost: 10000, add_to_cart: 0, purchases: 1, purchase_revenue: 10000,
    });
    const csv = buildPerformanceCsv(rows);
    const { uploadBatchId } = await uploadPerformanceCsv(request, advertiserId, csv);
    const uploadResult = await waitForUploadStatus(request, uploadBatchId);
    expect(uploadResult.status).toBe("SUCCESS");

    const { projectId } = await createProject(request, advertiserId, "E2ESimulation프로젝트", [
      { media: "META", campaignId: "c1" },
      { media: "GOOGLE", campaignId: "c1" },
    ]);
    expect(projectId).toBeGreaterThan(0);

    await page.goto("/simulation", { timeout: 60000 });

    const forbidden = /추천 예산|증액 추천|감액 추천|최적 예산|구매 최대화|매출 최대화/;
    expect(await page.getByText(forbidden).count()).toBe(0);

    await selectAdvertiser(page, advertiserId);
    await page.getByLabel("META").waitFor({ timeout: 20000 });

    const baseToStr = baseTo.toISOString().slice(0, 10);
    const baseFromStr = new Date(baseTo.getTime() - 6 * 86400000).toISOString().slice(0, 10);
    await page.getByRole("button", { name: "직접 설정" }).first().click();
    const dateInputs = page.locator('input[type="date"]');
    await dateInputs.nth(0).fill(baseFromStr);
    await dateInputs.nth(1).fill(baseToStr);

    await page.getByRole("button", { name: "직접 설정" }).nth(1).click();
    await dateInputs.nth(2).fill("2026-09-01");
    await dateInputs.nth(3).fill("2026-09-14");

    await page.getByLabel("META").fill("2000000");
    await page.getByRole("button", { name: "시뮬레이션 실행" }).click();
    await page.getByText(/높음|보통|낮음|예측 불가/).first().waitFor({ timeout: 20000 });

    // AC-43: 결과가 채워진 뒤(백엔드 notes 등 자유 텍스트 포함)에도 금지 표현이 없어야 한다.
    expect(await page.getByText(forbidden).count()).toBe(0);

    // AC-53: 새로고침하면 Simulation 입력값(프로젝트/예산/결과)이 전부 초기화돼야 한다
    // (DB/LocalStorage 저장 금지). 광고주는 전역 상태라 같은 광고주를 다시 선택해, 이번에는
    // Simulation 자체의 상태만 비어 있는지 확인한다.
    await page.reload();
    await page.getByRole("heading", { name: "Media Planning Simulation" }).waitFor({ timeout: 45000 });
    await selectAdvertiser(page, advertiserId);
    await page.getByLabel("META").waitFor({ timeout: 20000 });
    await expect(page.getByLabel("META")).toHaveValue("");
    expect(await page.getByText(/높음|보통|낮음|예측 불가/).count()).toBe(0);
  });
});
