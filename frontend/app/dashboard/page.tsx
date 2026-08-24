"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  Alert,
  Box,
  CircularProgress,
  Container,
  FormControlLabel,
  MenuItem,
  Paper,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from "@mui/material";
import { getDashboard, type DashboardResponse } from "@/lib/dashboardApi";
import { listProjects, type Media, type Project } from "@/lib/projectApi";
import KpiCards from "./KpiCards";
import MediaIndexChart from "./MediaIndexChart";
import PerformanceTable from "./PerformanceTable";
import IndexBreakdownChart from "./IndexBreakdownChart";
import RollingIndexChart from "./RollingIndexChart";
import JourneySummaryPlaceholder from "./JourneySummaryPlaceholder";

type PeriodPreset = "7d" | "30d" | "thisMonth" | "lastMonth" | "custom";

const PERIOD_OPTIONS: { value: PeriodPreset; label: string }[] = [
  { value: "7d", label: "최근 7일" },
  { value: "30d", label: "최근 30일" },
  { value: "thisMonth", label: "이번 달" },
  { value: "lastMonth", label: "지난 달" },
  { value: "custom", label: "직접 설정" },
];

function toISODate(date: Date) {
  return date.toISOString().slice(0, 10);
}

function computeRange(preset: PeriodPreset, customFrom: string, customTo: string): { from: string; to: string } {
  const today = new Date();
  if (preset === "custom") {
    return { from: customFrom, to: customTo };
  }
  if (preset === "7d") {
    const from = new Date(today);
    from.setDate(from.getDate() - 6);
    return { from: toISODate(from), to: toISODate(today) };
  }
  if (preset === "thisMonth") {
    const from = new Date(today.getFullYear(), today.getMonth(), 1);
    return { from: toISODate(from), to: toISODate(today) };
  }
  if (preset === "lastMonth") {
    const from = new Date(today.getFullYear(), today.getMonth() - 1, 1);
    const to = new Date(today.getFullYear(), today.getMonth(), 0);
    return { from: toISODate(from), to: toISODate(to) };
  }
  const from = new Date(today);
  from.setDate(from.getDate() - 29);
  return { from: toISODate(from), to: toISODate(today) };
}

export default function DashboardPage() {
  return (
    <Suspense fallback={null}>
      <DashboardPageContent />
    </Suspense>
  );
}

function DashboardPageContent() {
  const router = useRouter();
  // AC-23: 상세 화면에서 Breadcrumb으로 Dashboard에 돌아왔을 때 광고주/프로젝트/기간/이전 기간
  // 비교 상태를 복원한다(상세 화면 이동 시 이 값들을 query string으로 넘겨둠).
  const initialParams = useSearchParams();

  const [advertiserId, setAdvertiserId] = useState(() => initialParams.get("advertiserId") ?? "");
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
  const [comparePrevious, setComparePrevious] = useState(() => initialParams.get("comparePrevious") !== "false");

  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { from, to } = useMemo(() => computeRange(periodPreset, customFrom, customTo), [periodPreset, customFrom, customTo]);

  // PRD 6.1: 광고주 변경 시 프로젝트 목록을 다시 조회하고, 유효하지 않은 기존 선택은 해제한다.
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

  const selectedProject = projects.find((p) => p.projectId === selectedProjectId) ?? null;

  const refresh = useCallback(() => {
    if (!selectedProjectId || !from || !to) {
      setDashboard(null);
      return;
    }
    setLoading(true);
    setError(null);
    getDashboard(Number(selectedProjectId), from, to)
      .then(setDashboard)
      .catch(() => setError("Dashboard 데이터를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [selectedProjectId, from, to]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const handleMediaClick = (media: Media) => {
    const query = new URLSearchParams({
      advertiserId,
      projectId: String(selectedProjectId),
      from,
      to,
      comparePrevious: String(comparePrevious),
    });
    router.push(`/dashboard/media/${media}?${query.toString()}`);
  };

  return (
    <Container maxWidth="lg">
      <Box sx={{ py: 6 }}>
        <Stack spacing={4}>
          <Typography variant="h4" component="h1">
            Dashboard
          </Typography>

          <Paper sx={{ p: 3 }}>
            <Stack spacing={2}>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                <TextField
                  label="광고주 ID"
                  value={advertiserId}
                  onChange={(e) => setAdvertiserId(e.target.value)}
                  size="small"
                  fullWidth
                />
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
                <FormControlLabel
                  control={<Switch checked={comparePrevious} onChange={(e) => setComparePrevious(e.target.checked)} />}
                  label="이전 기간 비교"
                  sx={{ whiteSpace: "nowrap", flexShrink: 0 }}
                />
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
            </Stack>
          </Paper>

          {!advertiserId && <Alert severity="info">광고주 ID를 입력하세요.</Alert>}
          {advertiserId && projects.length === 0 && !projectsError && (
            <Alert severity="info">이 광고주에는 아직 프로젝트가 없습니다. 먼저 데이터를 업로드하세요.</Alert>
          )}
          {selectedProject && selectedProject.campaigns.length === 0 && (
            <Alert severity="warning">선택한 프로젝트에 포함된 캠페인이 없습니다.</Alert>
          )}

          {loading && (
            <Stack alignItems="center" sx={{ py: 6 }}>
              <CircularProgress />
            </Stack>
          )}
          {error && <Alert severity="error">{error}</Alert>}

          {!loading && !error && dashboard && selectedProject && selectedProject.campaigns.length > 0 && (
            <Stack spacing={4}>
              <KpiCards
                totals={dashboard.currentTotals}
                previousTotals={comparePrevious ? dashboard.previousTotals : undefined}
                comparePrevious={comparePrevious}
              />
              <Paper sx={{ p: 3 }}>
                <MediaIndexChart results={dashboard.current} onMediaClick={handleMediaClick} />
              </Paper>
              <Paper sx={{ p: 3 }}>
                <Typography variant="h6" sx={{ mb: 2 }}>
                  원본 + SingleONE 성과
                </Typography>
                <PerformanceTable results={dashboard.current} />
              </Paper>
              <Paper sx={{ p: 3 }}>
                <IndexBreakdownChart results={dashboard.current} />
              </Paper>
              <Paper sx={{ p: 3 }}>
                <RollingIndexChart points={dashboard.rolling} />
              </Paper>
              <Paper sx={{ p: 3 }}>
                <JourneySummaryPlaceholder />
              </Paper>
            </Stack>
          )}
        </Stack>
      </Box>
    </Container>
  );
}
