"use client";

import { Alert, Button, Stack, Typography } from "@mui/material";

/**
 * PRD 6.3 항목 6: Journey & Attribution 요약. 실제 여정/기여도 계산 엔진은 별도 단계에서
 * 구현하며, 이 화면에서는 이동 자리만 준비한다(사용자 확인: 자리표시자로만 준비).
 */
export default function JourneySummaryPlaceholder() {
  return (
    <Stack spacing={1}>
      <Typography variant="h6">Journey & Attribution 요약</Typography>
      <Alert severity="info">
        Journey & Attribution 분석은 준비 중입니다. 주요 사용자 여정, 1위 전환 기여 채널, 주요 채널 페어는 추후 제공됩니다.
      </Alert>
      <Button variant="outlined" disabled sx={{ alignSelf: "flex-start" }}>
        상세 분석으로 이동 (준비 중)
      </Button>
    </Stack>
  );
}
