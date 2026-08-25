"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Alert, Box, CircularProgress, Container, Stack } from "@mui/material";
import { getMediaDetail, listCampaigns, type MediaDetailResponse } from "@/lib/detailApi";
import type { EntityPerformance } from "@/lib/detailApi";
import type { Media } from "@/lib/projectApi";
import Breadcrumb from "@/app/detail/Breadcrumb";
import PerformanceSummary from "@/app/detail/PerformanceSummary";
import ChildEntityTable from "@/app/detail/ChildEntityTable";
import PageHeader from "@/app/components/common/PageHeader";
import SectionCard from "@/app/components/common/SectionCard";
import IndexBreakdownChart, { INDEX_BREAKDOWN_CHART_TITLE } from "@/app/dashboard/IndexBreakdownChart";
import RollingIndexChart, { ROLLING_INDEX_CHART_TITLE } from "@/app/dashboard/RollingIndexChart";

/** PRD 7.2 매체 상세: Index/이전 기간/원본+SingleONE 성과/구성요소 Breakdown/7일 Rolling/캠페인 목록. */
export default function MediaDetailPage() {
  const params = useParams<{ media: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const media = params.media as Media;

  const advertiserId = searchParams.get("advertiserId") ?? "";
  const projectId = Number(searchParams.get("projectId"));
  const from = searchParams.get("from") ?? "";
  const to = searchParams.get("to") ?? "";
  const comparePrevious = searchParams.get("comparePrevious") === "true";

  const [detail, setDetail] = useState<MediaDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!projectId || !from || !to) {
      return;
    }
    setLoading(true);
    getMediaDetail(projectId, media, from, to)
      .then((data) => {
        setDetail(data);
        setError(null);
      })
      .catch(() => setError("매체 상세 정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [projectId, media, from, to]);

  const carryOverQuery = useCallback(
    (extra: Record<string, string>) => {
      const query = new URLSearchParams({ advertiserId, projectId: String(projectId), from, to, comparePrevious: String(comparePrevious), ...extra });
      return query.toString();
    },
    [advertiserId, projectId, from, to, comparePrevious],
  );

  const handleCampaignClick = (row: EntityPerformance) => {
    router.push(`/dashboard/media/${media}/campaigns/${row.id}?${carryOverQuery({})}`);
  };

  return (
    <Container maxWidth="lg">
      <Box sx={{ py: 6 }}>
        <Stack spacing={3}>
          <Breadcrumb
            items={[
              { label: "Dashboard", href: `/dashboard?${carryOverQuery({})}` },
              { label: `매체: ${media}` },
            ]}
          />
          <PageHeader title={`매체 상세: ${media}`} />

          {loading && (
            <Stack alignItems="center" sx={{ py: 6 }}>
              <CircularProgress />
            </Stack>
          )}
          {error && <Alert severity="error">{error}</Alert>}

          {!loading && !error && detail && (
            <>
              <SectionCard>
                <PerformanceSummary current={detail.current} indexSection={{ current: detail.current, previous: detail.previous }} />
              </SectionCard>
              <SectionCard title={INDEX_BREAKDOWN_CHART_TITLE}>
                <IndexBreakdownChart results={[detail.current]} />
              </SectionCard>
              <SectionCard title={ROLLING_INDEX_CHART_TITLE}>
                <RollingIndexChart points={detail.rolling} />
              </SectionCard>
              <SectionCard title="캠페인 목록">
                <ChildEntityTable
                  title="캠페인"
                  fetchPage={(p) => listCampaigns(projectId, media, { from, to, ...p })}
                  onRowClick={handleCampaignClick}
                />
              </SectionCard>
            </>
          )}
        </Stack>
      </Box>
    </Container>
  );
}
