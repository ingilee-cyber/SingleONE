"use client";

import { Alert, Box, Chip, Paper, Stack, Table, TableBody, TableCell, TableRow, Tooltip, Typography } from "@mui/material";
import { fmt, fmtPercent } from "@/lib/format";
import type { ConfidenceLevel, MediaSimulationResult } from "@/lib/simulationApi";
import { mediaColor } from "@/lib/mediaColors";
import MarginalEfficiencyChart from "./MarginalEfficiencyChart";

// PRD 10.3 화면 Tooltip 문구(그대로 사용).
const CONVERTED_CURRENT_BUDGET_TOOLTIP =
  "선택한 기준 기간의 일평균 광고비를 시뮬레이션 기간 길이에 맞춰 환산한 참고값입니다.";

const CONFIDENCE_LABEL: Record<ConfidenceLevel, string> = {
  HIGH: "높음",
  MEDIUM: "보통",
  LOW: "낮음",
  UNAVAILABLE: "예측 불가",
};

function ConfidenceChip({ confidence }: { confidence: ConfidenceLevel | null }) {
  if (confidence === null) {
    return <Chip size="small" label="해당 없음" variant="outlined" />;
  }
  const color = confidence === "UNAVAILABLE" ? "default" : confidence === "HIGH" ? "success" : confidence === "MEDIUM" ? "info" : "warning";
  return <Chip size="small" label={CONFIDENCE_LABEL[confidence]} color={color} />;
}

/** PRD 10.8: 매체별 예상 성과/신뢰도/한계 효율 곡선/관찰 문구를 매체마다 카드 하나로 보여준다. */
export default function MediaResultTable({ mediaResults }: { mediaResults: MediaSimulationResult[] }) {
  return (
    <Stack spacing={2}>
      {mediaResults.map((result) => (
        <Paper key={result.media} variant="outlined" sx={{ p: 2, borderTop: 3, borderTopColor: mediaColor(result.media) }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Box sx={{ width: 10, height: 10, borderRadius: "50%", bgcolor: mediaColor(result.media) }} />
              <Typography variant="h6">{result.media}</Typography>
            </Stack>
            <ConfidenceChip confidence={result.confidence} />
          </Stack>
          <Table size="small">
            <TableBody>
              <TableRow>
                <TableCell>입력 예산</TableCell>
                <TableCell align="right">{fmt(result.inputBudget)}</TableCell>
                <TableCell>
                  환산 현재 운영{" "}
                  <Tooltip title={CONVERTED_CURRENT_BUDGET_TOOLTIP}>
                    <Typography component="span" variant="body2" color="text.secondary">
                      ⓘ
                    </Typography>
                  </Tooltip>
                </TableCell>
                <TableCell align="right">{fmt(result.convertedCurrentBudget)}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell>예상 SingleONE 구매</TableCell>
                <TableCell align="right">{fmt(result.predictedPurchases)}</TableCell>
                <TableCell>예상 SingleONE 구매매출</TableCell>
                <TableCell align="right">{fmt(result.predictedRevenue)}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell>예상 SingleONE CPA</TableCell>
                <TableCell align="right">{fmt(result.predictedCpa)}</TableCell>
                <TableCell>예상 SingleONE ROAS</TableCell>
                <TableCell align="right">{fmtPercent(result.predictedRoas)}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
          {result.notes.length > 0 && (
            <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: "wrap" }}>
              {result.notes.map((note) => (
                <Chip key={note} size="small" label={note} variant="outlined" />
              ))}
            </Stack>
          )}
          {result.confidence === "UNAVAILABLE" && (
            <Alert severity="info" sx={{ mt: 1 }}>
              이 매체는 과거 데이터 기준으로 예측 불가 상태입니다.
            </Alert>
          )}
          <MarginalEfficiencyChart result={result} />
        </Paper>
      ))}
    </Stack>
  );
}
