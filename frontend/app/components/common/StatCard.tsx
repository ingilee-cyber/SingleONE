"use client";

import { Card, CardContent, Stack, Tooltip, Typography } from "@mui/material";
import type { ReactNode } from "react";

interface StatCardProps {
  label: string;
  primary: string;
  secondary?: string;
  info?: string;
  change?: string | null;
  /** Card 없이 값만 필요한 곳(PerformanceSummary 등)에서 쓸 수 있도록 카드 테두리를 뺄 수 있게 한다. */
  bare?: boolean;
}

function StatContent({ label, primary, secondary, info, change }: Omit<StatCardProps, "bare">) {
  return (
    <Stack spacing={0.5}>
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
        <Typography variant="caption" color="text.secondary" display="block">
          {secondary}
        </Typography>
      )}
      {change && (
        <Typography variant="caption" color="text.secondary" display="block">
          {change}
        </Typography>
      )}
    </Stack>
  );
}

/** Dashboard KPI 카드/상세화면 지표를 공통화한 컴포넌트. `bare`면 카드 테두리 없이 값만 표시한다. */
export default function StatCard({ bare, ...content }: StatCardProps): ReactNode {
  if (bare) {
    return <StatContent {...content} />;
  }
  return (
    <Card>
      <CardContent>
        <StatContent {...content} />
      </CardContent>
    </Card>
  );
}
