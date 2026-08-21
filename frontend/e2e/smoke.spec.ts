import { test, expect } from "@playwright/test";

test("home page shows backend connected", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "SingleONE" })).toBeVisible();
  await expect(page.getByText("연결됨")).toBeVisible({ timeout: 10000 });
});
