"use client";

import { Stack, Typography } from "@mui/material";
import type { ReactNode } from "react";

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
}

/** 모든 화면 공통 페이지 타이틀 영역. 제목 h1 요소/텍스트는 화면마다 기존 그대로 유지한다. */
export default function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2} flexWrap="wrap">
      <Stack spacing={0.5}>
        <Typography variant="h4" component="h1">
          {title}
        </Typography>
        {description && (
          <Typography variant="body2" color="text.secondary">
            {description}
          </Typography>
        )}
      </Stack>
      {actions}
    </Stack>
  );
}
