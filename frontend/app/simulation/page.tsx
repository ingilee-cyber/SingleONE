"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Container,
  MenuItem,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from "@mui/material";
import { listProjects, type Media, type Project } from "@/lib/projectApi";
import { PERIOD_OPTIONS, computeRange, toISODate, type PeriodPreset } from "@/lib/period";
import { fmt, fmtPercent } from "@/lib/format";
import { useAdvertiserStore } from "@/lib/advertiserStore";
import PageHeader from "@/app/components/common/PageHeader";
import FilterPanel from "@/app/components/common/FilterPanel";
import SectionCard from "@/app/components/common/SectionCard";
import { useSimulationStore } from "./simulationStore";
import MediaResultTable from "./MediaResultTable";

export default function SimulationPage() {
  return (
    <Suspense fallback={null}>
      <SimulationPageContent />
    </Suspense>
  );
}

function PeriodPicker({
  label,
  preset,
  onPresetChange,
  customFrom,
  customTo,
  onCustomFromChange,
  onCustomToChange,
}: {
  label: string;
  preset: PeriodPreset;
  onPresetChange: (value: PeriodPreset) => void;
  customFrom: string;
  customTo: string;
  onCustomFromChange: (value: string) => void;
  onCustomToChange: (value: string) => void;
}) {
  return (
    <Stack spacing={1}>
      <Typography variant="subtitle2">{label}</Typography>
      <ToggleButtonGroup value={preset} exclusive size="small" onChange={(_, value) => value && onPresetChange(value)}>
        {PERIOD_OPTIONS.map((option) => (
          <ToggleButton key={option.value} value={option.value}>
            {option.label}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>
      {preset === "custom" && (
        <Stack direction="row" spacing={2}>
          <TextField
            label="시작일"
            type="date"
            value={customFrom}
            onChange={(e) => onCustomFromChange(e.target.value)}
            size="small"
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="종료일"
            type="date"
            value={customTo}
            onChange={(e) => onCustomToChange(e.target.value)}
            size="small"
            InputLabelProps={{ shrink: true }}
          />
        </Stack>
      )}
    </Stack>
  );
}

function SimulationPageContent() {
  const advertiserId = useAdvertiserStore((s) => s.selectedAdvertiserId);
  const store = useSimulationStore();

  const [projects, setProjects] = useState<Project[]>([]);
  const [projectsError, setProjectsError] = useState<string | null>(null);

  const [basePreset, setBasePreset] = useState<PeriodPreset>("30d");
  const [baseCustomFrom, setBaseCustomFrom] = useState(() => toISODate(new Date()));
  const [baseCustomTo, setBaseCustomTo] = useState(() => toISODate(new Date()));
  const [simPreset, setSimPreset] = useState<PeriodPreset>("30d");
  const [simCustomFrom, setSimCustomFrom] = useState(() => toISODate(new Date()));
  const [simCustomTo, setSimCustomTo] = useState(() => toISODate(new Date()));

  useEffect(() => {
    if (!advertiserId) {
      setProjects([]);
      return;
    }
    listProjects(advertiserId)
      .then((data) => {
        // PRD 10.2/AC-22: 시스템 "전체 캠페인" 프로젝트는 Simulation에서 아예 선택할 수 없다.
        const selectable = data.filter((p) => !p.systemDefault);
        setProjects(selectable);
        setProjectsError(null);
        if (!selectable.some((p) => p.projectId === store.projectId)) {
          store.setProjectId(selectable[0]?.projectId ?? "");
        }
      })
      .catch(() => setProjectsError("프로젝트 목록을 불러오지 못했습니다."));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [advertiserId]);

  useEffect(() => {
    const { from, to } = computeRange(basePreset, baseCustomFrom, baseCustomTo);
    store.setBaseFrom(from);
    store.setBaseTo(to);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [basePreset, baseCustomFrom, baseCustomTo]);

  useEffect(() => {
    const { from, to } = computeRange(simPreset, simCustomFrom, simCustomTo);
    store.setSimFrom(from);
    store.setSimTo(to);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [simPreset, simCustomFrom, simCustomTo]);

  const selectedProject = projects.find((p) => p.projectId === store.projectId) ?? null;
  const mediaList = useMemo<Media[]>(() => {
    if (!selectedProject) {
      return [];
    }
    return Array.from(new Set(selectedProject.campaigns.map((c) => c.media)));
  }, [selectedProject]);

  const totalBudget = mediaList.reduce((sum, media) => sum + Number(store.mediaBudgets[media] || 0), 0);

  return (
    <Container maxWidth="lg">
      <Box sx={{ py: 6 }}>
        <Stack spacing={4}>
          <PageHeader title="Media Planning Simulation" />
          <Alert severity="info">
            이 화면은 예산을 추천하거나 자동으로 배분하지 않습니다. 사용자가 입력한 매체별 예산을 과거 성과 모델에 대입했을 때의
            예상 결과만 보여주는 의사결정 참고용 기능입니다.
          </Alert>

          <FilterPanel>
              <TextField
                select
                label="프로젝트"
                value={store.projectId}
                onChange={(e) => store.setProjectId(e.target.value === "" ? "" : Number(e.target.value))}
                size="small"
                sx={{ maxWidth: 360 }}
                disabled={projects.length === 0}
                helperText="전체 캠페인 프로젝트는 선택할 수 없습니다."
              >
                {projects.map((project) => (
                  <MenuItem key={project.projectId} value={project.projectId}>
                    {project.projectName}
                  </MenuItem>
                ))}
              </TextField>
              {projectsError && <Alert severity="error">{projectsError}</Alert>}
              {advertiserId && projects.length === 0 && !projectsError && (
                <Alert severity="info">선택 가능한 프로젝트가 없습니다(전체 캠페인 프로젝트는 제외됩니다).</Alert>
              )}

              <PeriodPicker
                label="기준 성과 기간"
                preset={basePreset}
                onPresetChange={setBasePreset}
                customFrom={baseCustomFrom}
                customTo={baseCustomTo}
                onCustomFromChange={setBaseCustomFrom}
                onCustomToChange={setBaseCustomTo}
              />
              <PeriodPicker
                label="시뮬레이션 기간"
                preset={simPreset}
                onPresetChange={setSimPreset}
                customFrom={simCustomFrom}
                customTo={simCustomTo}
                onCustomFromChange={setSimCustomFrom}
                onCustomToChange={setSimCustomTo}
              />

              {mediaList.length > 0 && (
                <Stack spacing={1}>
                  <Typography variant="subtitle2">매체별 예산</Typography>
                  <Box
                    sx={{
                      display: "grid",
                      gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))",
                      gap: 2,
                    }}
                  >
                    {mediaList.map((media) => (
                      <TextField
                        key={media}
                        label={media}
                        type="number"
                        size="small"
                        fullWidth
                        value={store.mediaBudgets[media] ?? ""}
                        onChange={(e) => store.setMediaBudget(media, e.target.value)}
                      />
                    ))}
                  </Box>
                  <Typography variant="body2" color="text.secondary">
                    총예산(자동 합산): {fmt(totalBudget)}
                  </Typography>
                </Stack>
              )}

              <Button
                variant="contained"
                sx={{ alignSelf: "flex-start" }}
                disabled={!store.projectId || mediaList.length === 0 || store.loading}
                onClick={() => store.runSimulation()}
              >
                시뮬레이션 실행
              </Button>
          </FilterPanel>

          {store.loading && (
            <Stack alignItems="center" sx={{ py: 6 }}>
              <CircularProgress />
            </Stack>
          )}
          {store.error && <Alert severity="error">{store.error}</Alert>}

          {store.result && (
            <Stack spacing={3}>
              <SectionCard title="전체 예상 성과">
                {store.result.totalAvailable ? (
                  <Stack direction="row" spacing={4} sx={{ flexWrap: "wrap" }}>
                    <Typography>예상 SingleONE 구매: {fmt(store.result.totalPredictedPurchases)}</Typography>
                    <Typography>예상 SingleONE 구매매출: {fmt(store.result.totalPredictedRevenue)}</Typography>
                    <Typography>예상 SingleONE CPA: {fmt(store.result.totalPredictedCpa)}</Typography>
                    <Typography>예상 SingleONE ROAS: {fmtPercent(store.result.totalPredictedRoas)}</Typography>
                  </Stack>
                ) : (
                  <Alert severity="warning">
                    예산이 0보다 큰 매체 중 예측 불가인 매체가 있어 전체 예상 성과는 산출 불가입니다. 매체별 개별 결과는
                    아래에서 계속 확인할 수 있습니다.
                  </Alert>
                )}
              </SectionCard>

              <MediaResultTable mediaResults={store.result.mediaResults} />

              <Alert severity="info">{store.result.disclaimer}</Alert>
            </Stack>
          )}
        </Stack>
      </Box>
    </Container>
  );
}
