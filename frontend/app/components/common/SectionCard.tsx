"use client";

import { Box, Paper, Stack, Tooltip, Typography } from "@mui/material";
import type { ReactNode } from "react";

interface SectionCardProps {
  title?: string;
  info?: string;
  actions?: ReactNode;
  children: ReactNode;
  /** Dashboard/Journey/Simulation의 각 섹션(Paper)이 쓰던 sx 오버라이드를 그대로 넘길 수 있게 한다. */
  sx?: object;
}

/** 화면 곳곳에 반복되던 `<Paper sx={{p:3}}><Typography variant="h6">...` 패턴을 공통화한 카드. */
export default function SectionCard({ title, info, actions, children, sx }: SectionCardProps) {
  return (
    <Paper sx={{ p: 3, ...sx }}>
      {(title || actions) && (
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <Stack direction="row" spacing={0.5} alignItems="center">
            {title && <Typography variant="h6">{title}</Typography>}
            {info && (
              <Tooltip title={info}>
                <Typography component="span" color="text.secondary">
                  ⓘ
                </Typography>
              </Tooltip>
            )}
          </Stack>
          {actions}
        </Stack>
      )}
      <Box>{children}</Box>
    </Paper>
  );
}
