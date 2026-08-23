import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import UploadsPage from "./page";
import * as uploadApi from "@/lib/uploadApi";

vi.mock("@/lib/uploadApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/uploadApi")>("@/lib/uploadApi");
  return {
    ...actual,
    listUploads: vi.fn(),
    uploadPerformance: vi.fn(),
    uploadJourney: vi.fn(),
    confirmOverwrite: vi.fn(),
    cancelUpload: vi.fn(),
    getUploadErrors: vi.fn(),
  };
});

const successBatch: uploadApi.UploadBatch = {
  uploadBatchId: 1,
  advertiserId: "adv-1",
  type: "PERFORMANCE",
  filename: "perf.csv",
  status: "SUCCESS",
  totalRows: 10,
  successRows: 10,
  errorRows: 0,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

const duplicateBatch: uploadApi.UploadBatch = {
  ...successBatch,
  uploadBatchId: 2,
  status: "DUPLICATE_CONFIRMATION_REQUIRED",
  successRows: null,
};

const failedBatch: uploadApi.UploadBatch = {
  ...successBatch,
  uploadBatchId: 3,
  status: "FAILED",
  successRows: 0,
  errorRows: 1,
};

describe("UploadsPage", () => {
  beforeEach(() => {
    vi.mocked(uploadApi.listUploads).mockResolvedValue([successBatch]);
    vi.mocked(uploadApi.uploadPerformance).mockResolvedValue(successBatch);
    vi.mocked(uploadApi.confirmOverwrite).mockResolvedValue(successBatch);
    vi.mocked(uploadApi.cancelUpload).mockResolvedValue(successBatch);
    vi.mocked(uploadApi.getUploadErrors).mockResolvedValue([
      { rowNo: 1, errorCode: "NEGATIVE_VALUE", message: "cost 값은 음수일 수 없습니다: -1" },
    ]);
  });

  it("renders upload history from the API", async () => {
    render(<UploadsPage />);

    expect(await screen.findByText("perf.csv")).toBeInTheDocument();
    expect(screen.getByText("성공")).toBeInTheDocument();
  });

  it("submits the selected file to the performance upload endpoint", async () => {
    render(<UploadsPage />);
    await screen.findByText("perf.csv");

    fireEvent.change(screen.getByRole("textbox", { name: "광고주 ID" }), { target: { value: "adv-9" } });
    const file = new File(["date,advertiser_id\n"], "new.csv", { type: "text/csv" });
    fireEvent.change(screen.getByLabelText(/파일 선택/), { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: "업로드" }));

    await waitFor(() => {
      expect(uploadApi.uploadPerformance).toHaveBeenCalledWith("adv-9", file);
    });
  });

  it("shows confirm/cancel actions for a duplicate-confirmation batch and calls confirmOverwrite", async () => {
    vi.mocked(uploadApi.listUploads).mockResolvedValue([duplicateBatch]);
    render(<UploadsPage />);

    fireEvent.click(await screen.findByRole("button", { name: "확인(덮어쓰기)" }));

    await waitFor(() => {
      expect(uploadApi.confirmOverwrite).toHaveBeenCalledWith(2);
    });
  });

  it("shows row-specific errors in a dialog for a failed batch", async () => {
    vi.mocked(uploadApi.listUploads).mockResolvedValue([failedBatch]);
    render(<UploadsPage />);

    fireEvent.click(await screen.findByRole("button", { name: "오류 상세" }));

    expect(await screen.findByText("NEGATIVE_VALUE")).toBeInTheDocument();
    expect(screen.getByText("cost 값은 음수일 수 없습니다: -1")).toBeInTheDocument();
  });
});
