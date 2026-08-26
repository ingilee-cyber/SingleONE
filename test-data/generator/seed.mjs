// test-data/demo-full 산출물을 실제 Backend REST API로 업로드/생성한다(신규 API 없음,
// 기존 Upload/Project API만 재사용 — frontend/e2e/testData.ts와 같은 패턴).
// 사용법: BACKEND_URL(기본 http://localhost:8080)이 떠 있는 상태에서 `npm run seed`.
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { ADVERTISERS } from "./lib/brands.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..", "demo-full");
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

async function uploadCsv(kind, advertiserId, csvText, filename) {
  const form = new FormData();
  form.append("advertiserId", advertiserId);
  form.append("file", new Blob([csvText], { type: "text/csv" }), filename);
  const res = await fetch(`${BACKEND_URL}/api/v1/uploads/${kind}`, { method: "POST", body: form });
  if (!res.ok) {
    throw new Error(`${kind} 업로드 요청 실패(advertiser=${advertiserId}, status=${res.status}): ${await res.text()}`);
  }
  return res.json();
}

async function waitForUploadStatus(batchId, timeoutMs = 120000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const res = await fetch(`${BACKEND_URL}/api/v1/uploads/${batchId}`);
    const body = await res.json();
    if (body.status !== "VALIDATING" && body.status !== "IMPORTING") {
      return body;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`업로드 상태가 시간 내에 확정되지 않음(batchId=${batchId})`);
}

async function createProject(advertiserId, projectName, campaigns) {
  const res = await fetch(`${BACKEND_URL}/api/v1/advertisers/${advertiserId}/projects`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ projectName, campaigns }),
  });
  if (!res.ok) {
    throw new Error(`프로젝트 생성 실패(advertiser=${advertiserId}, project=${projectName}, status=${res.status}): ${await res.text()}`);
  }
  return res.json();
}

async function main() {
  const projectsJson = JSON.parse(await readFile(path.join(ROOT, "projects", "projects.json"), "utf-8"));

  for (const adv of ADVERTISERS) {
    console.log(`[seed] ${adv.id} Performance 업로드 중...`);
    const perfCsv = await readFile(path.join(ROOT, "performance", `performance_${adv.id}.csv`), "utf-8");
    const perfBatch = await uploadCsv("performance", adv.id, perfCsv, `performance_${adv.id}.csv`);
    const perfResult = await waitForUploadStatus(perfBatch.uploadBatchId);
    if (perfResult.status !== "SUCCESS") {
      throw new Error(`[seed] ${adv.id} Performance 업로드 실패: status=${perfResult.status} (batchId=${perfBatch.uploadBatchId})`);
    }
    console.log(`[seed] ${adv.id} Performance 업로드 완료 (${perfResult.successRows}행)`);

    const advProjects = projectsJson.projects.filter((p) => p.advertiserId === adv.id);
    for (const project of advProjects) {
      console.log(`[seed] ${adv.id} 프로젝트 "${project.projectName}" 생성 중...`);
      await createProject(adv.id, project.projectName, project.campaigns);
    }

    console.log(`[seed] ${adv.id} Journey 업로드 중...`);
    const journeyCsv = await readFile(path.join(ROOT, "journey", `journey_${adv.id}.csv`), "utf-8");
    const journeyBatch = await uploadCsv("journey", adv.id, journeyCsv, `journey_${adv.id}.csv`);
    const journeyResult = await waitForUploadStatus(journeyBatch.uploadBatchId);
    if (journeyResult.status !== "SUCCESS") {
      throw new Error(`[seed] ${adv.id} Journey 업로드 실패: status=${journeyResult.status} (batchId=${journeyBatch.uploadBatchId})`);
    }
    console.log(`[seed] ${adv.id} Journey 업로드 완료 (${journeyResult.successRows}건)`);
  }

  console.log("[seed] 전체 완료.");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
