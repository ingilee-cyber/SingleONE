import { expect, test } from "@playwright/test";
import { createProject, selectAdvertiser, uploadJourneyCsv, waitForUploadStatus } from "./testData";

/**
 * PRD 9장 Golden Journey Dataset(15.4) 골든 패스 + AC-42(금지 표현)/9.7 중립 표현 확인.
 */
test.describe("Journey & Attribution 골든 패스", () => {
  test("Golden Journey Dataset 업로드 후 3개 탭 확인, 금지/인과 표현 미노출", async ({ page, request }) => {
    const advertiserId = `e2e-journey-${Date.now()}`;
    const journeyCsv = [
      "event_id,anonymous_user_id,advertiser_id,event_type,event_timestamp,media,campaign_id,ad_group_id,ad_id,order_id,purchase_revenue",
      `e1,U001,${advertiserId},CLICK,2026-07-08T10:00:00Z,META,c1,,,,`,
      `e2,U001,${advertiserId},CLICK,2026-07-09T10:00:00Z,GOOGLE,c1,,,,`,
      `e3,U001,${advertiserId},PURCHASE,2026-07-10T10:00:00Z,,,,,order-u001,100000`,
      `e4,U002,${advertiserId},CLICK,2026-07-08T11:00:00Z,TIKTOK,c1,,,,`,
      `e5,U002,${advertiserId},CLICK,2026-07-09T11:00:00Z,META,c1,,,,`,
      `e6,U002,${advertiserId},PURCHASE,2026-07-10T11:00:00Z,,,,,order-u002,120000`,
      `e7,U003,${advertiserId},CLICK,2026-07-09T12:00:00Z,GOOGLE,c1,,,,`,
      `e8,U003,${advertiserId},PURCHASE,2026-07-10T12:00:00Z,,,,,order-u003,80000`,
      `e9,U004,${advertiserId},CLICK,2026-07-08T09:00:00Z,META,c1,,,,`,
      `e10,U004,${advertiserId},CLICK,2026-07-08T15:00:00Z,GOOGLE,c1,,,,`,
      `e11,U004,${advertiserId},CLICK,2026-07-09T09:00:00Z,TIKTOK,c1,,,,`,
      `e12,U004,${advertiserId},PURCHASE,2026-07-10T09:00:00Z,,,,,order-u004,150000`,
    ].join("\n") + "\n";

    const { uploadBatchId } = await uploadJourneyCsv(request, advertiserId, journeyCsv);
    const uploadResult = await waitForUploadStatus(request, uploadBatchId);
    expect(uploadResult.status).toBe("SUCCESS");

    // Journey 이벤트만으로는 campaign_master가 채워지지 않으므로(Master Upsert는 성과 업로드
    // 전용), 프로젝트 생성 전에 캠페인 마스터가 필요 없는 방식 대신, 여기서는 프로젝트 생성
    // API가 campaign_master 존재를 요구하지 않는 광고주 "전체 캠페인" 흐름이 아니라 직접
    // campaign_master가 필요하므로 성과 데이터를 곁들여 캠페인을 등록한다.
    const seedRes = await request.post("http://localhost:8080/api/v1/uploads/performance", {
      multipart: {
        advertiserId,
        file: {
          name: "seed.csv",
          mimeType: "text/csv",
          buffer: Buffer.from(
            "date,advertiser_id,advertiser_name,media,campaign_id,campaign_name,ad_group_id,ad_group_name,ad_id,ad_name,impressions,clicks,cost,add_to_cart,purchases,purchase_revenue\n" +
              `2026-07-10,${advertiserId},E2E광고주,META,c1,메타캠페인,ag-1,광고그룹1,ad-1,광고1,1000,100,10000,0,1,10000\n` +
              `2026-07-10,${advertiserId},E2E광고주,GOOGLE,c1,구글캠페인,ag-1,광고그룹1,ad-1,광고1,1000,100,10000,0,1,10000\n` +
              `2026-07-10,${advertiserId},E2E광고주,TIKTOK,c1,틱톡캠페인,ag-1,광고그룹1,ad-1,광고1,1000,100,10000,0,1,10000\n`,
            "utf-8",
          ),
        },
      },
    });
    const seedBatch = await seedRes.json();
    const seedResult = await waitForUploadStatus(request, seedBatch.uploadBatchId);
    expect(seedResult.status).toBe("SUCCESS");

    const { projectId } = await createProject(request, advertiserId, "E2EJourney프로젝트", [
      { media: "META", campaignId: "c1" },
      { media: "GOOGLE", campaignId: "c1" },
      { media: "TIKTOK", campaignId: "c1" },
    ]);
    expect(projectId).toBeGreaterThan(0);

    const query = new URLSearchParams({
      projectId: String(projectId), from: "2026-07-01", to: "2026-07-10",
    }).toString();
    await page.goto(`/journey?${query}`, { timeout: 60000 });
    await selectAdvertiser(page, advertiserId);
    await page.getByRole("heading", { name: "Journey & Attribution" }).waitFor({ timeout: 45000 });
    await page.locator("canvas, svg").first().waitFor({ timeout: 45000 });

    await page.getByRole("tab", { name: "채널별 전환 기여도" }).click();
    await page.getByText(/Journey & Attribution 분석은 이벤트 데이터를 기준으로 하며/).waitFor({ timeout: 15000 });
    // AC-42: "SingleONE 기여 구매"라는 표현을 어디에도 쓰지 않는다.
    expect(await page.getByText("SingleONE 기여 구매").count()).toBe(0);

    await page.getByRole("tab", { name: "채널 페어 인사이트" }).click();
    await page.getByText("META + GOOGLE").waitFor({ timeout: 15000 });
    // PRD 9.7: 최적/효율적/인과 표현을 쓰지 않는다.
    expect(await page.getByText(/최적|가장 효율적|때문에 성과/).count()).toBe(0);
  });
});
