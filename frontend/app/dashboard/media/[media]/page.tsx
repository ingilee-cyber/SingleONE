"use client";

import { useParams, useSearchParams } from "next/navigation";
import { Alert, Box, Container, Stack, Typography } from "@mui/material";

/**
 * PRD 7장 매체 상세는 아직 구현하지 않는다. 이 화면은 Dashboard에서 매체 클릭 시
 * 광고주/프로젝트/기간/이전 기간 비교 상태가 query string으로 정확히 전달되는지만 확인하는
 * 자리표시 스텁이다.
 */
export default function MediaDetailStubPage() {
  const params = useParams<{ media: string }>();
  const searchParams = useSearchParams();

  return (
    <Container maxWidth="sm">
      <Box sx={{ py: 8 }}>
        <Stack spacing={2}>
          <Typography variant="h4" component="h1">
            매체 상세: {params.media}
          </Typography>
          <Alert severity="info">매체 상세 화면은 다음 단계에서 제공됩니다.</Alert>
          <Typography variant="body2" color="text.secondary">
            전달받은 컨텍스트: 광고주 {searchParams.get("advertiserId")}, 프로젝트 {searchParams.get("projectId")}, 기간{" "}
            {searchParams.get("from")} ~ {searchParams.get("to")}, 이전 기간 비교{" "}
            {searchParams.get("comparePrevious") === "true" ? "ON" : "OFF"}
          </Typography>
        </Stack>
      </Box>
    </Container>
  );
}
