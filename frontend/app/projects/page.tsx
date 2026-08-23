"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import {
  createProject,
  deleteProject,
  listProjects,
  MEDIA_LIST,
  searchCampaigns,
  updateProject,
  type CampaignOption,
  type CampaignSelection,
  type Media,
  type Project,
} from "@/lib/projectApi";

const MIN_DISTINCT_MEDIA = 2;

function selectionKey(media: Media, campaignId: string) {
  return `${media}|${campaignId}`;
}

export default function ProjectsPage() {
  const [advertiserId, setAdvertiserId] = useState("");
  const [search, setSearch] = useState("");
  const [projects, setProjects] = useState<Project[]>([]);
  const [listError, setListError] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editingProject, setEditingProject] = useState<Project | null>(null);
  const [projectName, setProjectName] = useState("");
  const [selected, setSelected] = useState<CampaignSelection[]>([]);
  const [campaignOptions, setCampaignOptions] = useState<CampaignOption[]>([]);
  const [pickerSearch, setPickerSearch] = useState("");
  const [pickerMedia, setPickerMedia] = useState<Media | "">("");
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<Project | null>(null);

  const refresh = useCallback(() => {
    if (!advertiserId) {
      setProjects([]);
      return;
    }
    listProjects(advertiserId, search)
      .then((data) => {
        setProjects(data);
        setListError(null);
      })
      .catch(() => setListError("프로젝트 목록을 불러오지 못했습니다."));
  }, [advertiserId, search]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const loadCampaignOptions = useCallback(() => {
    if (!advertiserId) {
      return;
    }
    searchCampaigns(advertiserId, pickerSearch, pickerMedia || undefined).then(setCampaignOptions);
  }, [advertiserId, pickerSearch, pickerMedia]);

  useEffect(() => {
    if (formOpen) {
      loadCampaignOptions();
    }
  }, [formOpen, loadCampaignOptions]);

  const openCreateDialog = () => {
    setEditingProject(null);
    setProjectName("");
    setSelected([]);
    setPickerSearch("");
    setPickerMedia("");
    setFormError(null);
    setFormOpen(true);
  };

  const openEditDialog = (project: Project) => {
    setEditingProject(project);
    setProjectName(project.projectName);
    setSelected(project.campaigns.map((c) => ({ media: c.media, campaignId: c.campaignId })));
    setPickerSearch("");
    setPickerMedia("");
    setFormError(null);
    setFormOpen(true);
  };

  const toggleCampaign = (option: CampaignOption) => {
    const key = selectionKey(option.media, option.campaignId);
    setSelected((prev) =>
      prev.some((s) => selectionKey(s.media, s.campaignId) === key)
        ? prev.filter((s) => selectionKey(s.media, s.campaignId) !== key)
        : [...prev, { media: option.media, campaignId: option.campaignId }],
    );
  };

  const distinctMediaCount = new Set(selected.map((s) => s.media)).size;

  const handleSubmit = async () => {
    setFormError(null);
    setSubmitting(true);
    try {
      const request = { projectName, campaigns: selected };
      if (editingProject) {
        await updateProject(editingProject.projectId, request);
      } else {
        await createProject(advertiserId, request);
      }
      setFormOpen(false);
      refresh();
    } catch (e) {
      const message =
        e && typeof e === "object" && "response" in e
          ? // eslint-disable-next-line @typescript-eslint/no-explicit-any
            ((e as any).response?.data?.message ?? "요청이 실패했습니다.")
          : "요청이 실패했습니다.";
      setFormError(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    await deleteProject(deleteTarget.projectId);
    setDeleteTarget(null);
    refresh();
  };

  return (
    <Container maxWidth="md">
      <Box sx={{ py: 6 }}>
        <Stack spacing={4}>
          <Typography variant="h4" component="h1">
            프로젝트
          </Typography>

          <Paper sx={{ p: 3 }}>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                label="광고주 ID"
                value={advertiserId}
                onChange={(e) => setAdvertiserId(e.target.value)}
                size="small"
                fullWidth
              />
              <TextField
                label="프로젝트명 검색"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                size="small"
                fullWidth
              />
              <Button
                variant="contained"
                onClick={openCreateDialog}
                disabled={!advertiserId}
                sx={{ whiteSpace: "nowrap" }}
              >
                새 프로젝트
              </Button>
            </Stack>
          </Paper>

          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" sx={{ mb: 2 }}>
              프로젝트 목록
            </Typography>
            {listError && <Alert severity="error">{listError}</Alert>}
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>프로젝트명</TableCell>
                    <TableCell>매체 구성</TableCell>
                    <TableCell>캠페인 수</TableCell>
                    <TableCell>구분</TableCell>
                    <TableCell>액션</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {projects.map((project) => (
                    <TableRow key={project.projectId}>
                      <TableCell>{project.projectName}</TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5} flexWrap="wrap">
                          {[...new Set(project.campaigns.map((c) => c.media))].map((media) => (
                            <Chip key={media} label={media} size="small" />
                          ))}
                        </Stack>
                      </TableCell>
                      <TableCell>{project.campaigns.length}</TableCell>
                      <TableCell>
                        {project.systemDefault && <Chip label="참고용 비교(전체 캠페인)" size="small" color="default" />}
                      </TableCell>
                      <TableCell>
                        {!project.systemDefault && (
                          <Stack direction="row" spacing={1}>
                            <Button size="small" onClick={() => openEditDialog(project)}>
                              수정
                            </Button>
                            <Button size="small" color="error" onClick={() => setDeleteTarget(project)}>
                              삭제
                            </Button>
                          </Stack>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </Stack>
      </Box>

      <Dialog open={formOpen} onClose={() => setFormOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editingProject ? "프로젝트 수정" : "새 프로젝트"}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="프로젝트명"
              value={projectName}
              onChange={(e) => setProjectName(e.target.value)}
              size="small"
              fullWidth
            />
            <Stack direction="row" spacing={2}>
              <TextField
                label="캠페인 검색"
                value={pickerSearch}
                onChange={(e) => setPickerSearch(e.target.value)}
                size="small"
                fullWidth
              />
              <TextField
                select
                label="매체 필터"
                value={pickerMedia}
                onChange={(e) => setPickerMedia(e.target.value as Media | "")}
                size="small"
                sx={{ minWidth: 140 }}
              >
                <MenuItem value="">전체</MenuItem>
                {MEDIA_LIST.map((media) => (
                  <MenuItem key={media} value={media}>
                    {media}
                  </MenuItem>
                ))}
              </TextField>
              <Button onClick={loadCampaignOptions}>검색</Button>
            </Stack>
            <Box sx={{ maxHeight: 240, overflowY: "auto", border: "1px solid", borderColor: "divider", borderRadius: 1 }}>
              {campaignOptions.map((option) => (
                <FormControlLabel
                  key={selectionKey(option.media, option.campaignId)}
                  sx={{ display: "flex", ml: 0, px: 1 }}
                  control={
                    <Checkbox
                      checked={selected.some((s) => selectionKey(s.media, s.campaignId) === selectionKey(option.media, option.campaignId))}
                      onChange={() => toggleCampaign(option)}
                    />
                  }
                  label={`[${option.media}] ${option.campaignName} (${option.campaignId})`}
                />
              ))}
              {campaignOptions.length === 0 && (
                <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
                  조건에 맞는 캠페인이 없습니다.
                </Typography>
              )}
            </Box>
            <Typography variant="body2">
              선택된 캠페인: {selected.length}개 (매체 {distinctMediaCount}개, 최소 {MIN_DISTINCT_MEDIA}개 필요)
            </Typography>
            {selected.length > 0 && (
              <Stack direction="row" spacing={0.5} flexWrap="wrap">
                {selected.map((s) => (
                  <Chip
                    key={selectionKey(s.media, s.campaignId)}
                    label={`${s.media} ${s.campaignId}`}
                    size="small"
                    onDelete={() =>
                      setSelected((prev) => prev.filter((p) => selectionKey(p.media, p.campaignId) !== selectionKey(s.media, s.campaignId)))
                    }
                  />
                ))}
              </Stack>
            )}
            {formError && <Alert severity="error">{formError}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setFormOpen(false)}>취소</Button>
          <Button
            variant="contained"
            onClick={handleSubmit}
            disabled={submitting || !projectName.trim() || distinctMediaCount < MIN_DISTINCT_MEDIA}
          >
            저장
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={deleteTarget !== null} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>프로젝트 삭제</DialogTitle>
        <DialogContent>
          <Typography>&ldquo;{deleteTarget?.projectName}&rdquo; 프로젝트를 삭제하시겠습니까?</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>취소</Button>
          <Button color="error" onClick={handleDelete}>
            삭제
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
