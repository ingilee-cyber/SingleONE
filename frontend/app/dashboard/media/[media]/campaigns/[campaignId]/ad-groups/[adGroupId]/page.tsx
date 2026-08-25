"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { Alert, Box, CircularProgress, Container, Stack } from "@mui/material";
import { getAdGroupDetail, listAds } from "@/lib/detailApi";
import type { EntityPerformance } from "@/lib/detailApi";
import type { Media } from "@/lib/projectApi";
import Breadcrumb from "@/app/detail/Breadcrumb";
import PerformanceSummary from "@/app/detail/PerformanceSummary";
import ChildEntityTable from "@/app/detail/ChildEntityTable";
import PageHeader from "@/app/components/common/PageHeader";
import SectionCard from "@/app/components/common/SectionCard";

/** PRD 7.4 광고그룹 상세: 원본+SingleONE 성과, 광고 목록(이전 기간/Index 없음). */
export default function AdGroupDetailPage() {
  const params = useParams<{ media: string; campaignId: string; adGroupId: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const media = params.media as Media;
  const campaignId = decodeURIComponent(params.campaignId);
  const adGroupId = decodeURIComponent(params.adGroupId);

  const advertiserId = searchParams.get("advertiserId") ?? "";
  const projectId = Number(searchParams.get("projectId"));
  const from = searchParams.get("from") ?? "";
  const to = searchParams.get("to") ?? "";
  const comparePrevious = searchParams.get("comparePrevious") === "true";
  const campaignName = searchParams.get("campaignName") ?? campaignId;

  const [detail, setDetail] = useState<EntityPerformance | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!projectId || !from || !to) {
      return;
    }
    setLoading(true);
    getAdGroupDetail(projectId, media, campaignId, adGroupId, from, to)
      .then((data) => {
        setDetail(data);
        setError(null);
      })
      .catch(() => setError("광고그룹 상세 정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [projectId, media, campaignId, adGroupId, from, to]);

  const carryOverQuery = useCallback(
    (extra: Record<string, string>) => {
      const query = new URLSearchParams({ advertiserId, projectId: String(projectId), from, to, comparePrevious: String(comparePrevious), ...extra });
      return query.toString();
    },
    [advertiserId, projectId, from, to, comparePrevious],
  );

  const handleAdClick = (row: EntityPerformance) => {
    const query = carryOverQuery({ campaignName, adGroupName: detail?.name ?? adGroupId });
    router.push(`/dashboard/media/${media}/campaigns/${campaignId}/ad-groups/${adGroupId}/ads/${row.id}?${query}`);
  };

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
                href: `/dashboard/media/${media}/campaigns/${campaignId}?${carryOverQuery({})}`,
              },
              { label: `광고그룹: ${detail?.name ?? adGroupId}` },
            ]}
          />
          <PageHeader title={`광고그룹 상세: ${detail?.name ?? adGroupId}`} />

          {loading && (
            <Stack alignItems="center" sx={{ py: 6 }}>
              <CircularProgress />
            </Stack>
          )}
          {error && <Alert severity="error">{error}</Alert>}

          {!loading && !error && detail && (
            <>
              <SectionCard>
                <PerformanceSummary current={detail} />
              </SectionCard>
              <SectionCard title="광고 목록">
                <ChildEntityTable
                  title="광고"
                  fetchPage={(p) => listAds(projectId, media, campaignId, adGroupId, { from, to, ...p })}
                  onRowClick={handleAdClick}
                />
              </SectionCard>
            </>
          )}
        </Stack>
      </Box>
    </Container>
  );
}
