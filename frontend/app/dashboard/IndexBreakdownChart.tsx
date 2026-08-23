"use client";

import { Alert, Box, Typography } from "@mui/material";
import EChart from "./EChart";
import type { MediaIndexResult } from "@/lib/dashboardApi";

interface IndexBreakdownChartProps {
  results: MediaIndexResult[];
}

/** PRD 6.3 항목 4: SingleONE Index 구성요소(노출/클릭/구매/매출 효율) Breakdown, VALID 매체만. */
export default function IndexBreakdownChart({ results }: IndexBreakdownChartProps) {
  const valid = results.filter((r) => r.status === "VALID" && r.components !== null);

  if (valid.length === 0) {
    return (
      <Box>
        <Typography variant="h6">SingleONE Index 구성요소 Breakdown</Typography>
        <Alert severity="info" sx={{ mt: 2 }}>
          Breakdown을 표시할 수 있는 매체가 없습니다.
        </Alert>
      </Box>
    );
  }

  const option = {
    legend: { data: ["노출 효율", "클릭 효율", "구매 효율", "매출 효율"] },
    xAxis: { type: "category", data: valid.map((r) => r.media) },
    yAxis: { type: "value" },
    tooltip: { trigger: "axis" },
    series: [
      { name: "노출 효율", type: "bar", data: valid.map((r) => Math.round(r.components!.exposureIndex)) },
      { name: "클릭 효율", type: "bar", data: valid.map((r) => Math.round(r.components!.clickIndex)) },
      { name: "구매 효율", type: "bar", data: valid.map((r) => Math.round(r.components!.purchaseIndex)) },
      { name: "매출 효율", type: "bar", data: valid.map((r) => Math.round(r.components!.revenueIndex)) },
    ],
  };

  return (
    <Box>
      <Typography variant="h6">SingleONE Index 구성요소 Breakdown</Typography>
      <EChart option={option} />
    </Box>
  );
}
