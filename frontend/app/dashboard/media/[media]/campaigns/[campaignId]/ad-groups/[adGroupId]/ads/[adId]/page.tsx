"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import { Alert, Box, CircularProgress, Container, Paper, Stack, Typography } from "@mui/material";
import { getAdDetail } from "@/lib/detailApi";
import type { EntityPerformance } from "@/lib/detailApi";
import type { Media } from "@/lib/projectApi";
import Breadcrumb from "@/app/detail/Breadcrumb";
import PerformanceSummary from "@/app/detail/PerformanceSummary";

/** PRD 7.5 광고 상세: 원본+SingleONE 성과만(Index/이전 기간/하위 목록 없음). */
export default function AdDetailPage() {
  const params = useParams<{ media: string; campaignId: string; adGroupId: string; adId: string }>();
  const searchParams = useSearchParams();
  const media = params.media as Media;
  const campaignId = decodeURIComponent(params.campaignId);
  const adGroupId = decodeURIComponent(params.adGroupId);
  const adId = decodeURIComponent(params.adId);

  const advertiserId = searchParams.get("advertiserId") ?? "";
  const projectId = Number(searchParams.get("projectId"));
  const from = searchParams.get("from") ?? "";
  const to = searchParams.get("to") ?? "";
  const comparePrevious = searchParams.get("comparePrevious") === "true";
  const campaignName = searchParams.get("campaignName") ?? campaignId;
  const adGroupName = searchParams.get("adGroupName") ?? adGroupId;

  const [detail, setDetail] = useState<EntityPerformance | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!projectId || !from || !to) {
      return;
    }
    setLoading(true);
    getAdDetail(projectId, media, campaignId, adGroupId, adId, from, to)
      .then((data) => {
        setDetail(data);
        setError(null);
      })
      .catch(() => setError("광고 상세 정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [projectId, media, campaignId, adGroupId, adId, from, to]);

  const carryOverQuery = useCallback(
    (extra: Record<string, string>) => {
      const query = new URLSearchParams({ advertiserId, projectId: String(projectId), from, to, comparePrevious: String(comparePrevious), ...extra });
      return query.toString();
    },
    [advertiserId, projectId, from, to, comparePrevious],
  );

  return (
    <Container maxWidth="lg">
      <Box sx={{ py: 6 }}>
        <Stack spacing={3}>
          <Breadcrumb
            items={[
              { label: "Dashboard", href: `/dashboard?${carryOverQuery({})}` },
              { label: `매체: ${media}`, href: `/dashboard/media/${media}?${carryOverQuery({})}` },
              {
                label: `캠페인: ${campaignName}`,
                href: `/dashboard/media/${media}/campaigns/${campaignId}?${carryOverQuery({ campaignName })}`,
              },
              {
                label: `광고그룹: ${adGroupName}`,
                href: `/dashboard/media/${media}/campaigns/${campaignId}/ad-groups/${adGroupId}?${carryOverQuery({ campaignName })}`,
              },
              { label: `광고: ${detail?.name ?? adId}` },
            ]}
          />
          <Typography variant="h4" component="h1">
            광고 상세: {detail?.name ?? adId}
          </Typography>

          {loading && (
            <Stack alignItems="center" sx={{ py: 6 }}>
              <CircularProgress />
            </Stack>
          )}
          {error && <Alert severity="error">{error}</Alert>}

          {!loading && !error && detail && (
            <Paper sx={{ p: 3 }}>
              <PerformanceSummary current={detail} />
            </Paper>
          )}
        </Stack>
      </Box>
    </Container>
  );
}
