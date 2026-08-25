"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
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
  cancelUpload,
  confirmOverwrite,
  getUploadErrors,
  listUploads,
  PENDING_STATUSES,
  uploadJourney,
  uploadPerformance,
  type UploadBatch,
  type UploadErrorDetail,
  type UploadStatus,
  type UploadType,
} from "@/lib/uploadApi";
import PageHeader from "@/app/components/common/PageHeader";
import SectionCard from "@/app/components/common/SectionCard";

const STATUS_LABEL: Record<UploadStatus, string> = {
  VALIDATING: "검증 중",
  DUPLICATE_CONFIRMATION_REQUIRED: "중복 확인 필요",
  IMPORTING: "반영 중",
  SUCCESS: "성공",
  FAILED: "실패",
  CANCELLED: "취소됨",
};

const STATUS_COLOR: Record<UploadStatus, "default" | "warning" | "success" | "error"> = {
  VALIDATING: "default",
  DUPLICATE_CONFIRMATION_REQUIRED: "warning",
  IMPORTING: "default",
  SUCCESS: "success",
  FAILED: "error",
  CANCELLED: "default",
};

export default function UploadsPage() {
  const [advertiserId, setAdvertiserId] = useState("");
  const [uploadType, setUploadType] = useState<UploadType>("PERFORMANCE");
  const [file, setFile] = useState<File | null>(null);
  const [fileInputKey, setFileInputKey] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [batches, setBatches] = useState<UploadBatch[]>([]);
  const [errorDialog, setErrorDialog] = useState<{ batchId: number; errors: UploadErrorDetail[] } | null>(null);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const refresh = useCallback(() => {
    listUploads().then(setBatches).catch(() => undefined);
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    const hasPending = batches.some((b) => PENDING_STATUSES.includes(b.status));
    if (!hasPending) {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
      return;
    }
    if (!pollingRef.current) {
      pollingRef.current = setInterval(refresh, 2000);
    }
    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    };
  }, [batches, refresh]);

  const handleSubmit = async () => {
    if (!advertiserId || !file) {
      setFormError("광고주 ID와 파일을 모두 입력하세요.");
      return;
    }
    setFormError(null);
    setSubmitting(true);
    try {
      const upload = uploadType === "PERFORMANCE" ? uploadPerformance : uploadJourney;
      await upload(advertiserId, file);
      setFile(null);
      setFileInputKey((key) => key + 1);
      refresh();
    } catch (e) {
      const message =
        e && typeof e === "object" && "response" in e
          ? // eslint-disable-next-line @typescript-eslint/no-explicit-any
            ((e as any).response?.data?.message ?? "업로드 요청이 실패했습니다.")
          : "업로드 요청이 실패했습니다.";
      setFormError(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirm = async (batchId: number) => {
    await confirmOverwrite(batchId);
    refresh();
  };

  const handleCancel = async (batchId: number) => {
    await cancelUpload(batchId);
    refresh();
  };

  const handleShowErrors = async (batchId: number) => {
    const errors = await getUploadErrors(batchId);
    setErrorDialog({ batchId, errors });
  };

  return (
    <Container maxWidth="md">
      <Box sx={{ py: 6 }}>
        <Stack spacing={4}>
          <PageHeader title="데이터 관리" />

          <SectionCard title="파일 업로드">
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
                  label="종류"
                  value={uploadType}
                  onChange={(e) => setUploadType(e.target.value as UploadType)}
                  size="small"
                  sx={{ minWidth: 160 }}
                >
                  <MenuItem value="PERFORMANCE">성과 데이터</MenuItem>
                  <MenuItem value="JOURNEY">Journey 이벤트</MenuItem>
                </TextField>
              </Stack>
              <Button variant="outlined" component="label">
                파일 선택 (.csv, .xlsx)
                <input
                  key={fileInputKey}
                  type="file"
                  hidden
                  accept=".csv,.xlsx"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
              </Button>
              {file && <Typography variant="body2">선택된 파일: {file.name}</Typography>}
              {formError && <Alert severity="error">{formError}</Alert>}
              <Button variant="contained" onClick={handleSubmit} disabled={submitting}>
                업로드
              </Button>
            </Stack>
          </SectionCard>

          <SectionCard title="업로드 이력">
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>업로드 일시</TableCell>
                    <TableCell>종류</TableCell>
                    <TableCell>파일명</TableCell>
                    <TableCell>광고주</TableCell>
                    <TableCell>반영 건수</TableCell>
                    <TableCell>상태</TableCell>
                    <TableCell>액션</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {batches.map((batch) => (
                    <TableRow key={batch.uploadBatchId}>
                      <TableCell>{batch.createdAt ? new Date(batch.createdAt).toLocaleString("ko-KR") : "-"}</TableCell>
                      <TableCell>{batch.type === "PERFORMANCE" ? "성과" : "Journey"}</TableCell>
                      <TableCell>{batch.filename}</TableCell>
                      <TableCell>{batch.advertiserId}</TableCell>
                      <TableCell>
                        {batch.successRows ?? "-"} / {batch.errorRows ?? "-"}
                      </TableCell>
                      <TableCell>
                        <Chip label={STATUS_LABEL[batch.status]} color={STATUS_COLOR[batch.status]} size="small" />
                      </TableCell>
                      <TableCell>
                        {batch.status === "DUPLICATE_CONFIRMATION_REQUIRED" && (
                          <Stack direction="row" spacing={1}>
                            <Button size="small" onClick={() => handleConfirm(batch.uploadBatchId)}>
                              확인(덮어쓰기)
                            </Button>
                            <Button size="small" color="error" onClick={() => handleCancel(batch.uploadBatchId)}>
                              취소
                            </Button>
                          </Stack>
                        )}
                        {batch.status === "FAILED" && (
                          <Button size="small" onClick={() => handleShowErrors(batch.uploadBatchId)}>
                            오류 상세
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </SectionCard>
        </Stack>
      </Box>

      <Dialog open={errorDialog !== null} onClose={() => setErrorDialog(null)} maxWidth="sm" fullWidth>
        <DialogTitle>오류 상세 (배치 #{errorDialog?.batchId})</DialogTitle>
        <DialogContent>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>행 번호</TableCell>
                <TableCell>오류 코드</TableCell>
                <TableCell>메시지</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {errorDialog?.errors.map((err, idx) => (
                <TableRow key={idx}>
                  <TableCell>{err.rowNo}</TableCell>
                  <TableCell>{err.errorCode}</TableCell>
                  <TableCell>{err.message}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setErrorDialog(null)}>닫기</Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
