"use client";

import { Paper, Stack } from "@mui/material";
import type { ReactNode } from "react";

/**
 * Dashboard/Journey/Simulation/Projects/Uploads의 필터·입력 영역(광고주ID/프로젝트/기간 등)을
 * 감싸는 공통 컨테이너. 화면마다 다른 필드 구성은 children으로 그대로 넘긴다(필드 자체는 손대지 않음).
 */
export default function FilterPanel({ children }: { children: ReactNode }) {
  return (
    <Paper sx={{ p: 3 }}>
      <Stack spacing={2}>{children}</Stack>
    </Paper>
  );
}
