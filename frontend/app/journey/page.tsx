"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import {
  Alert,
  Box,
  CircularProgress,
  Container,
  MenuItem,
  Stack,
  Tab,
  Tabs,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
} from "@mui/material";
import { listProjects, type Project } from "@/lib/projectApi";
import { PERIOD_OPTIONS, computeRange, toISODate, type PeriodPreset } from "@/lib/period";
import { getJourneyAnalysis, type JourneyAnalysisResult } from "@/lib/journeyApi";
import { useAdvertiserStore } from "@/lib/advertiserStore";
import PageHeader from "@/app/components/common/PageHeader";
import FilterPanel from "@/app/components/common/FilterPanel";
import SectionCard from "@/app/components/common/SectionCard";
import SankeyChart from "./SankeyChart";
import TopPathTable from "./TopPathTable";
import ChannelAttributionTable from "./ChannelAttributionTable";
import ChannelPairTable from "./ChannelPairTable";

export default function JourneyPage() {
  return (
    <Suspense fallback={null}>
      <JourneyPageContent />
    </Suspense>
  );
}

function JourneyPageContent() {
  const initialParams = useSearchParams();

  const advertiserId = useAdvertiserStore((s) => s.selectedAdvertiserId);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<number | "">(() => {
    const raw = initialParams.get("projectId");
    return raw ? Number(raw) : "";
  });
  const [projectsError, setProjectsError] = useState<string | null>(null);

  const [periodPreset, setPeriodPreset] = useState<PeriodPreset>(() =>
    initialParams.get("from") && initialParams.get("to") ? "custom" : "30d",
  );
  const [customFrom, setCustomFrom] = useState(() => initialParams.get("from") ?? toISODate(new Date()));
  const [customTo, setCustomTo] = useState(() => initialParams.get("to") ?? toISODate(new Date()));

  const [tab, setTab] = useState(0);
  const [result, setResult] = useState<JourneyAnalysisResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { from, to } = useMemo(() => computeRange(periodPreset, customFrom, customTo), [periodPreset, customFrom, customTo]);

  useEffect(() => {
    if (!advertiserId) {
      setProjects([]);
      setSelectedProjectId("");
      return;
    }
    listProjects(advertiserId)
      .then((data) => {
        setProjects(data);
        setProjectsError(null);
        setSelectedProjectId((prev) => (data.some((p) => p.projectId === prev) ? prev : data[0]?.projectId ?? ""));
      })
      .catch(() => setProjectsError("프로젝트 목록을 불러오지 못했습니다."));
  }, [advertiserId]);

  const refresh = useCallback(() => {
    if (!selectedProjectId || !from || !to) {
      setResult(null);
      return;
    }
    setLoading(true);
    setError(null);
    getJourneyAnalysis(Number(selectedProjectId), from, to)
      .then(setResult)
      .catch(() => setError("Journey 데이터를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [selectedProjectId, from, to]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return (
    <Container maxWidth="lg">
      <Box sx={{ py: 6 }}>
        <Stack spacing={4}>
          <PageHeader title="Journey & Attribution" />

          <FilterPanel>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                select
                label="프로젝트"
                value={selectedProjectId}
                onChange={(e) => setSelectedProjectId(e.target.value === "" ? "" : Number(e.target.value))}
                size="small"
                fullWidth
                disabled={projects.length === 0}
              >
                {projects.map((project) => (
                  <MenuItem key={project.projectId} value={project.projectId}>
                    {project.projectName}
                    {project.referenceOnly ? " (참고용 비교)" : ""}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
            <ToggleButtonGroup
              value={periodPreset}
              exclusive
              size="small"
              onChange={(_, value) => value && setPeriodPreset(value)}
            >
              {PERIOD_OPTIONS.map((option) => (
                <ToggleButton key={option.value} value={option.value}>
                  {option.label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
            {periodPreset === "custom" && (
              <Stack direction="row" spacing={2}>
                <TextField
                  label="시작일"
                  type="date"
                  value={customFrom}
                  onChange={(e) => setCustomFrom(e.target.value)}
                  size="small"
                  InputLabelProps={{ shrink: true }}
                />
                <TextField
                  label="종료일"
                  type="date"
                  value={customTo}
                  onChange={(e) => setCustomTo(e.target.value)}
                  size="small"
                  InputLabelProps={{ shrink: true }}
                />
              </Stack>
            )}
            {projectsError && <Alert severity="error">{projectsError}</Alert>}
          </FilterPanel>

          {!advertiserId && <Alert severity="info">등록된 광고주가 없습니다. 데이터 관리에서 먼저 데이터를 업로드하세요.</Alert>}
          {advertiserId && projects.length === 0 && !projectsError && (
            <Alert severity="info">이 광고주에는 아직 프로젝트가 없습니다.</Alert>
          )}

          {loading && (
            <Stack alignItems="center" sx={{ py: 6 }}>
              <CircularProgress />
            </Stack>
          )}
          {error && <Alert severity="error">{error}</Alert>}

          {!loading && !error && result && (
            <SectionCard>
              <Tabs value={tab} onChange={(_, value) => setTab(value)} sx={{ mb: 3 }}>
                <Tab label="사용자 여정" />
                <Tab label="채널별 전환 기여도" />
                <Tab label="채널 페어 인사이트" />
              </Tabs>

              {result.attributedJourneyCount === 0 ? (
                <Alert severity="info">선택한 기간에 분석 가능한 Journey 이벤트가 없습니다.</Alert>
              ) : (
                <>
                  {tab === 0 && (
                    <Stack spacing={3}>
                      <SankeyChart topPaths={result.topPaths} />
                      <TopPathTable topPaths={result.topPaths} />
                    </Stack>
                  )}
                  {tab === 1 && <ChannelAttributionTable rows={result.attribution} />}
                  {tab === 2 && <ChannelPairTable rows={result.channelPairs} />}
                </>
              )}
            </SectionCard>
          )}
        </Stack>
      </Box>
    </Container>
  );
}
