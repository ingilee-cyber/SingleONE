"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Alert, Box, CircularProgress, Container, Paper, Stack, Typography } from "@mui/material";
import { getCampaignDetail, listAdGroups, type EntityPerformanceComparison } from "@/lib/detailApi";
import type { EntityPerformance } from "@/lib/detailApi";
import type { Media } from "@/lib/projectApi";
import Breadcrumb from "@/app/detail/Breadcrumb";
import PerformanceSummary from "@/app/detail/PerformanceSummary";
import ChildEntityTable from "@/app/detail/ChildEntityTable";

/** PRD 7.3 캠페인 상세: 원본+SingleONE 성과, 이전 기간 비교, 광고그룹 목록(Index 없음). */
export default function CampaignDetailPage() {
  const params = useParams<{ media: string; campaignId: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const media = params.media as Media;
  const campaignId = decodeURIComponent(params.campaignId);

  const advertiserId = searchParams.get("advertiserId") ?? "";
  const projectId = Number(searchParams.get("projectId"));
  const from = searchParams.get("from") ?? "";
  const to = searchParams.get("to") ?? "";
  const comparePrevious = searchParams.get("comparePrevious") === "true";

  const [detail, setDetail] = useState<EntityPerformanceComparison | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!projectId || !from || !to) {
      return;
    }
    setLoading(true);
    getCampaignDetail(projectId, media, campaignId, from, to)
      .then((data) => {
        setDetail(data);
        setError(null);
      })
      .catch(() => setError("캠페인 상세 정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [projectId, media, campaignId, from, to]);

  const carryOverQuery = useCallback(
    (extra: Record<string, string>) => {
      const query = new URLSearchParams({ advertiserId, projectId: String(projectId), from, to, comparePrevious: String(comparePrevious), ...extra });
      return query.toString();
    },
    [advertiserId, projectId, from, to, comparePrevious],
  );

  const handleAdGroupClick = (row: EntityPerformance) => {
    const query = carryOverQuery({ campaignName: detail?.current.name ?? campaignId });
    router.push(`/dashboard/media/${media}/campaigns/${campaignId}/ad-groups/${row.id}?${query}`);
  };

  return (
    <Container maxWidth="lg">
      <Box sx={{ py: 6 }}>
        <Stack spacing={3}>
          <Breadcrumb
            items={[
              { label: "Dashboard", href: `/dashboard?${carryOverQuery({})}` },
              { label: `매체: ${media}`, href: `/dashboard/media/${media}?${carryOverQuery({})}` },
              { label: `캠페인: ${detail?.current.name ?? campaignId}` },
            ]}
          />
          <Typography variant="h4" component="h1">
            캠페인 상세: {detail?.current.name ?? campaignId}
          </Typography>

          {loading && (
            <Stack alignItems="center" sx={{ py: 6 }}>
              <CircularProgress />
            </Stack>
          )}
          {error && <Alert severity="error">{error}</Alert>}

          {!loading && !error && detail && (
            <>
              <Paper sx={{ p: 3 }}>
                <PerformanceSummary current={detail.current} previous={detail.previous} />
              </Paper>
              <Paper sx={{ p: 3 }}>
                <Typography variant="h6" sx={{ mb: 2 }}>
                  광고그룹 목록
                </Typography>
                <ChildEntityTable
                  title="광고그룹"
                  fetchPage={(p) => listAdGroups(projectId, media, campaignId, { from, to, ...p })}
                  onRowClick={handleAdGroupClick}
                />
              </Paper>
            </>
          )}
        </Stack>
      </Box>
    </Container>
  );
}
