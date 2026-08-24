"use client";

import { Box, Card, CardContent, Chip, Stack, Tooltip, Typography } from "@mui/material";
import { fmt, fmtPercent } from "@/lib/format";
import {
  INDEX_STATUS_LABEL,
  type MediaIndexResult,
  type MediaPerformanceTotals,
  type OriginalPerformance,
  type SingleOnePerformance,
} from "@/lib/dashboardApi";

const INDEX_TOOLTIP =
  "비교 프로젝트 내 유효 매체들의 비용 대비 광고 효율 평균을 100으로 환산한 상대 효율 점수입니다. " +
  "100보다 높을수록 비교 대상 매체 평균보다 상대적으로 높은 효율을 의미합니다.";

export interface PerformanceLike {
  rawTotals: MediaPerformanceTotals | null;
  rawPerformance: OriginalPerformance | null;
  singleOnePerformance: SingleOnePerformance | null;
}

interface PerformanceSummaryProps {
  current: PerformanceLike;
  /** PRD 7.3: 캠페인 상세에만 있는 이전 기간 비교. 광고그룹/광고 상세는 넘기지 않는다. */
  previous?: PerformanceLike;
  /** PRD 7.2: 매체 상세에만 있는 Index 영역. */
  indexSection?: { current: MediaIndexResult; previous: MediaIndexResult };
}

function Metric({ label, primary, secondary, info }: { label: string; primary: string; secondary?: string; info?: string }) {
  return (
    <Stack spacing={0.5} sx={{ minWidth: 160 }}>
      <Stack direction="row" spacing={0.5} alignItems="center">
        <Typography variant="body2" color="text.secondary">
          {label}
        </Typography>
        {info && (
          <Tooltip title={info}>
            <Typography variant="body2" color="text.secondary" component="span">
              ⓘ
            </Typography>
          </Tooltip>
        )}
      </Stack>
      <Typography variant="h6">{primary}</Typography>
      {secondary && (
        <Typography variant="caption" color="text.secondary">
          {secondary}
        </Typography>
      )}
    </Stack>
  );
}

/**
 * PRD 7.2~7.5 공용 "원본 vs SingleONE 성과" 영역. indexSection/previous를 넘기지 않으면
 * 해당 화면 규칙대로 자동으로 표시되지 않는다(광고그룹/광고 상세는 둘 다 생략).
 */
export default function PerformanceSummary({ current, previous, indexSection }: PerformanceSummaryProps) {
  return (
    <Stack spacing={3}>
      {indexSection && (
        <Card variant="outlined">
          <CardContent>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="subtitle1">SingleONE Index</Typography>
              <Tooltip title={INDEX_TOOLTIP}>
                <Typography color="text.secondary" component="span">
                  ⓘ
                </Typography>
              </Tooltip>
            </Stack>
            <Stack direction="row" spacing={3} alignItems="baseline" sx={{ mt: 1 }} flexWrap="wrap">
              <Typography variant="h4">
                {indexSection.current.indexScore !== null ? Math.round(indexSection.current.indexScore) : "-"}
              </Typography>
              <Chip size="small" label={INDEX_STATUS_LABEL[indexSection.current.status]} />
              {indexSection.previous.indexScore !== null && indexSection.current.indexScore !== null && (
                <Typography variant="body2" color="text.secondary">
                  이전 기간 {Math.round(indexSection.previous.indexScore)} (
                  {indexSection.current.indexScore >= indexSection.previous.indexScore ? "▲" : "▼"}{" "}
                  {Math.abs(Math.round(indexSection.current.indexScore - indexSection.previous.indexScore))})
                </Typography>
              )}
              {indexSection.previous.indexScore === null && (
                <Typography variant="body2" color="text.secondary">
                  이전 기간 데이터 부족
                </Typography>
              )}
            </Stack>
          </CardContent>
        </Card>
      )}

      <Stack direction="row" spacing={4} flexWrap="wrap">
        <Metric label="Cost" primary={fmt(current.rawTotals?.cost)} />
        <Metric label="Impressions" primary={fmt(current.rawTotals?.impressions)} />
        <Metric label="Clicks" primary={fmt(current.rawTotals?.clicks)} />
        <Metric
          label="Purchases"
          primary={`원본 ${fmt(current.rawTotals?.rawPurchases)}`}
          secondary={`SingleONE ${fmt(current.singleOnePerformance?.singleOnePurchases)}`}
          info="자체 내부 전환 기준입니다."
        />
        <Metric
          label="Purchase Revenue"
          primary={`원본 ${fmt(current.rawTotals?.rawRevenue)}`}
          secondary={`SingleONE ${fmt(current.singleOnePerformance?.singleOneRevenue)}`}
        />
        <Metric
          label="CPA"
          primary={`원본 ${fmt(current.rawPerformance?.cpa)}`}
          secondary={`SingleONE ${fmt(current.singleOnePerformance?.cpa)}`}
        />
        <Metric
          label="ROAS"
          primary={`원본 ${fmtPercent(current.rawPerformance?.roas)}`}
          secondary={`SingleONE ${fmtPercent(current.singleOnePerformance?.roas)}`}
        />
      </Stack>

      {previous && (
        <Box>
          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
            이전 기간
          </Typography>
          <Stack direction="row" spacing={4} flexWrap="wrap">
            <Metric label="Cost" primary={fmt(previous.rawTotals?.cost)} />
            <Metric
              label="Purchases"
              primary={`원본 ${fmt(previous.rawTotals?.rawPurchases)}`}
              secondary={`SingleONE ${fmt(previous.singleOnePerformance?.singleOnePurchases)}`}
            />
            <Metric
              label="Purchase Revenue"
              primary={`원본 ${fmt(previous.rawTotals?.rawRevenue)}`}
              secondary={`SingleONE ${fmt(previous.singleOnePerformance?.singleOneRevenue)}`}
            />
            <Metric
              label="ROAS"
              primary={`원본 ${fmtPercent(previous.rawPerformance?.roas)}`}
              secondary={`SingleONE ${fmtPercent(previous.singleOnePerformance?.roas)}`}
            />
          </Stack>
        </Box>
      )}
    </Stack>
  );
}
