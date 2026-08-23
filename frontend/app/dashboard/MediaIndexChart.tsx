"use client";

import { Alert, Box, Chip, Stack, Tooltip, Typography } from "@mui/material";
import EChart from "./EChart";
import { INDEX_STATUS_LABEL, type MediaIndexResult } from "@/lib/dashboardApi";
import type { Media } from "@/lib/projectApi";

const INDEX_TOOLTIP =
  "비교 프로젝트 내 유효 매체들의 비용 대비 광고 효율 평균을 100으로 환산한 상대 효율 점수입니다. " +
  "100보다 높을수록 비교 대상 매체 평균보다 상대적으로 높은 효율을 의미합니다.";

interface MediaIndexChartProps {
  results: MediaIndexResult[];
  onMediaClick: (media: Media) => void;
}

/** PRD 6.3 항목 2: 매체별 SingleONE Index 점수(점수 높은 순, Backend가 이미 정렬해 내려줌). */
export default function MediaIndexChart({ results, onMediaClick }: MediaIndexChartProps) {
  const validResults = results.filter((r) => r.status === "VALID" && r.indexScore !== null);
  const invalidResults = results.filter((r) => r.status !== "VALID");

  const option = {
    xAxis: { type: "category", data: validResults.map((r) => r.media) },
    yAxis: { type: "value", name: "Index" },
    tooltip: { trigger: "axis" },
    series: [
      {
        type: "bar",
        data: validResults.map((r) => Math.round(r.indexScore as number)),
        itemStyle: { color: "#1976d2" },
      },
    ],
  };

  return (
    <Box>
      <Stack direction="row" spacing={0.5} alignItems="center">
        <Typography variant="h6">매체별 SingleONE Index</Typography>
        <Tooltip title={INDEX_TOOLTIP}>
          <Typography component="span" color="text.secondary">
            ⓘ
          </Typography>
        </Tooltip>
      </Stack>
      {validResults.length > 0 ? (
        <EChart option={option} onClickCategory={(name) => onMediaClick(name as Media)} />
      ) : (
        <Alert severity="info" sx={{ mt: 2 }}>
          Index를 계산할 수 있는 매체가 없습니다.
        </Alert>
      )}
      {invalidResults.length > 0 && (
        <Stack direction="row" spacing={1} sx={{ mt: 2 }} flexWrap="wrap">
          {invalidResults.map((r) => (
            <Chip key={r.media} label={`${r.media}: ${INDEX_STATUS_LABEL[r.status]}`} size="small" onClick={() => onMediaClick(r.media)} />
          ))}
        </Stack>
      )}
    </Box>
  );
}
