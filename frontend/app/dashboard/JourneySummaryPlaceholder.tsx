"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Alert, Button, Stack, Typography } from "@mui/material";
import { fmt } from "@/lib/format";
import { getJourneyAnalysis, type JourneyAnalysisResult } from "@/lib/journeyApi";

export const JOURNEY_SUMMARY_TITLE = "Journey & Attribution 요약";

interface JourneySummaryProps {
  advertiserId: string;
  projectId: number;
  from: string;
  to: string;
}

/** PRD 6.3 항목 6: Journey & Attribution 요약. 상세 화면(9장)과 동일한 API를 재사용한다. */
export default function JourneySummaryPlaceholder({ advertiserId, projectId, from, to }: JourneySummaryProps) {
  const router = useRouter();
  const [result, setResult] = useState<JourneyAnalysisResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!projectId || !from || !to) {
      return;
    }
    getJourneyAnalysis(projectId, from, to)
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch(() => setError("Journey 데이터를 불러오지 못했습니다."));
  }, [projectId, from, to]);

  const handleNavigate = () => {
    const query = new URLSearchParams({ advertiserId, projectId: String(projectId), from, to });
    router.push(`/journey?${query.toString()}`);
  };

  const topPath = result?.topPaths[0];
  const topChannel = result?.attribution[0];
  const topPair = result?.channelPairs[0];

  return (
    <Stack spacing={1}>
      {error && <Alert severity="error">{error}</Alert>}
      {!error && result && result.attributedJourneyCount === 0 && (
        <Alert severity="info">선택한 기간에 분석 가능한 Journey 이벤트가 없습니다.</Alert>
      )}
      {!error && result && result.attributedJourneyCount > 0 && (
        <Stack spacing={0.5}>
          <Typography variant="body2">
            주요 사용자 여정: {topPath ? `${topPath.channels.join(" → ")} → 구매` : "-"}
          </Typography>
          <Typography variant="body2">
            1위 전환 기여 채널: {topChannel ? `${topChannel.channel} (기여 구매 ${fmt(topChannel.attributedPurchases)}건)` : "-"}
          </Typography>
          <Typography variant="body2">
            주요 채널 페어: {topPair ? `${topPair.channelA} + ${topPair.channelB} (${topPair.journeyCount}건)` : "-"}
          </Typography>
        </Stack>
      )}
      <Button variant="outlined" sx={{ alignSelf: "flex-start" }} onClick={handleNavigate}>
        상세 분석으로 이동
      </Button>
    </Stack>
  );
}
