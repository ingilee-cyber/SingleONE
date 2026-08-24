import { expect, test } from "@playwright/test";
import { buildPerformanceCsv } from "./testData";

/**
 * PRD 11장 업로드 상태 머신 골든 패스를 실제 화면(파일 선택 -> 업로드 버튼)으로 검증한다.
 * AC-26(오류 파일 -> FAILED), AC-28(중복 -> 확인 후 SUCCESS), AC-29(중복 -> 취소 -> CANCELLED).
 *
 * 업로드 이력 화면은 광고주 필터 없이 전체 이력을 한 표에 보여주므로(app/uploads/page.tsx), 매
 * 상호작용을 이번 테스트가 올린 파일명이 포함된 행으로 반드시 좁혀서 다른 실행의 이력과 섞이지
 * 않게 한다.
 */
test.describe("업로드 생명주기 골든 패스", () => {
  test("AC-26: 오류가 있는 파일은 FAILED로 처리되고 행별 오류가 보인다", async ({ page }) => {
    const advertiserId = `e2e-upload-fail-${Date.now()}`;
    const filename = `invalid-${Date.now()}.csv`;
    const csv = buildPerformanceCsv([
      {
        date: "2026-07-01", advertiser_id: advertiserId, advertiser_name: "E2E광고주", media: "META", campaign_id: "c1",
        campaign_name: "메타캠페인", ad_group_id: "ag-1", ad_group_name: "광고그룹1", ad_id: "ad-1", ad_name: "광고1",
        impressions: 1000, clicks: 100, cost: -1, add_to_cart: 0, purchases: 1, purchase_revenue: 10000,
      },
    ]);

    await page.goto("/uploads", { timeout: 60000 });
    await page.getByRole("textbox", { name: "광고주 ID" }).fill(advertiserId);
    await page.getByLabel(/파일 선택/).setInputFiles({ name: filename, mimeType: "text/csv", buffer: Buffer.from(csv, "utf-8") });
    await page.getByRole("button", { name: "업로드" }).click();

    const row = page.getByRole("row", { name: new RegExp(filename.replace(".", "\\.")) });
    await row.getByText("실패").waitFor({ timeout: 20000 });
    await row.getByRole("button", { name: "오류 상세" }).click();
    await page.getByText(/음수/).waitFor({ timeout: 10000 });
  });

  test("AC-28/AC-29: 중복 업로드는 확인 시 SUCCESS, 취소 시 CANCELLED가 된다", async ({ page }) => {
    const advertiserId = `e2e-upload-dup-${Date.now()}`;
    const stamp = Date.now();
    const csv = buildPerformanceCsv([
      {
        date: "2026-07-01", advertiser_id: advertiserId, advertiser_name: "E2E광고주", media: "META", campaign_id: "c1",
        campaign_name: "메타캠페인", ad_group_id: "ag-1", ad_group_name: "광고그룹1", ad_id: "ad-1", ad_name: "광고1",
        impressions: 1000, clicks: 100, cost: 100000, add_to_cart: 0, purchases: 5, purchase_revenue: 100000,
      },
    ]);

    await page.goto("/uploads", { timeout: 60000 });
    await page.getByRole("textbox", { name: "광고주 ID" }).fill(advertiserId);

    // 1차 업로드 -> SUCCESS.
    const firstName = `first-${stamp}.csv`;
    await page.getByLabel(/파일 선택/).setInputFiles({ name: firstName, mimeType: "text/csv", buffer: Buffer.from(csv, "utf-8") });
    await page.getByRole("button", { name: "업로드" }).click();
    const firstRow = page.getByRole("row", { name: new RegExp(firstName.replace(".", "\\.")) });
    await firstRow.getByText("성공").waitFor({ timeout: 20000 });

    // 2차(동일 데이터) -> 중복 확인 요청 -> 확인(덮어쓰기) -> SUCCESS.
    const secondName = `second-${stamp}.csv`;
    await page.getByLabel(/파일 선택/).setInputFiles({ name: secondName, mimeType: "text/csv", buffer: Buffer.from(csv, "utf-8") });
    await page.getByRole("button", { name: "업로드" }).click();
    const secondRow = page.getByRole("row", { name: new RegExp(secondName.replace(".", "\\.")) });
    await secondRow.getByRole("button", { name: "확인(덮어쓰기)" }).click();
    await secondRow.getByText("성공").waitFor({ timeout: 20000 });

    // 3차(다시 동일 데이터) -> 중복 확인 요청 -> 취소 -> CANCELLED.
    const thirdName = `third-${stamp}.csv`;
    await page.getByLabel(/파일 선택/).setInputFiles({ name: thirdName, mimeType: "text/csv", buffer: Buffer.from(csv, "utf-8") });
    await page.getByRole("button", { name: "업로드" }).click();
    const thirdRow = page.getByRole("row", { name: new RegExp(thirdName.replace(".", "\\.")) });
    await thirdRow.getByRole("button", { name: "취소" }).click();
    await thirdRow.getByText("취소됨").waitFor({ timeout: 20000 });
  });
});
