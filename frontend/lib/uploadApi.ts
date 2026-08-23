import apiClient from "@/lib/apiClient";

// Backend com.singleone.backend.domain.upload.UploadType/UploadStatus, upload.UploadBatchResponse/UploadErrorResponse와 1:1 대응.
export type UploadType = "PERFORMANCE" | "JOURNEY";

export type UploadStatus =
  | "VALIDATING"
  | "DUPLICATE_CONFIRMATION_REQUIRED"
  | "IMPORTING"
  | "SUCCESS"
  | "FAILED"
  | "CANCELLED";

export interface UploadBatch {
  uploadBatchId: number;
  advertiserId: string;
  type: UploadType;
  filename: string;
  status: UploadStatus;
  totalRows: number | null;
  successRows: number | null;
  errorRows: number | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface UploadErrorDetail {
  rowNo: number;
  errorCode: string;
  message: string;
}

export const PENDING_STATUSES: UploadStatus[] = ["VALIDATING", "IMPORTING"];

function uploadFile(path: string, advertiserId: string, file: File) {
  const form = new FormData();
  form.append("advertiserId", advertiserId);
  form.append("file", file);
  return apiClient.post<UploadBatch>(path, form).then((res) => res.data);
}

export const uploadPerformance = (advertiserId: string, file: File) =>
  uploadFile("/api/v1/uploads/performance", advertiserId, file);

export const uploadJourney = (advertiserId: string, file: File) =>
  uploadFile("/api/v1/uploads/journey", advertiserId, file);

export const listUploads = (page = 0, size = 50) =>
  apiClient
    .get<{ content: UploadBatch[] }>("/api/v1/uploads", { params: { page, size } })
    .then((res) => res.data.content);

export const getUpload = (batchId: number) =>
  apiClient.get<UploadBatch>(`/api/v1/uploads/${batchId}`).then((res) => res.data);

export const getUploadErrors = (batchId: number) =>
  apiClient.get<UploadErrorDetail[]>(`/api/v1/uploads/${batchId}/errors`).then((res) => res.data);

export const confirmOverwrite = (batchId: number) =>
  apiClient.post<UploadBatch>(`/api/v1/uploads/${batchId}/confirm-overwrite`).then((res) => res.data);

export const cancelUpload = (batchId: number) =>
  apiClient.post<UploadBatch>(`/api/v1/uploads/${batchId}/cancel`).then((res) => res.data);
