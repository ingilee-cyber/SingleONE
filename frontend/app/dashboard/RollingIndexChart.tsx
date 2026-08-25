"use client";

import { Alert, Box } from "@mui/material";
import EChart from "./EChart";
import type { RollingIndexPoint } from "@/lib/dashboardApi";
import type { Media } from "@/lib/projectApi";
import { mediaColor } from "@/lib/mediaColors";

export const ROLLING_INDEX_CHART_TITLE = "7일 Rolling SingleONE Index";

interface RollingIndexChartProps {
  points: RollingIndexPoint[];
}

/** PRD 6.3 항목 5 / 8.9: 7일 Rolling SingleONE Index. 유효하지 않은 날짜의 매체는 값 없음(끊긴 라인)으로 표시한다. */
export default function RollingIndexChart({ points }: RollingIndexChartProps) {
  if (points.length === 0) {
    return <Alert severity="info">표시할 수 있는 기간이 없습니다.</Alert>;
  }

  const dates = points.map((p) => p.date);
  const mediaList = Array.from(new Set(points.flatMap((p) => p.mediaResults.map((r) => r.media))));

  const series = mediaList.map((media: Media) => ({
    name: media,
    type: "line",
    connectNulls: false,
    itemStyle: { color: mediaColor(media) },
    lineStyle: { color: mediaColor(media) },
    data: points.map((p) => {
      const result = p.mediaResults.find((r) => r.media === media);
      return result && result.status === "VALID" && result.indexScore !== null ? Math.round(result.indexScore) : null;
    }),
  }));

  const option = {
    legend: { data: mediaList },
    xAxis: { type: "category", data: dates },
    yAxis: { type: "value", name: "Index" },
    tooltip: { trigger: "axis" },
    series,
  };

  return (
    <Box>
      <EChart option={option} />
    </Box>
  );
}
