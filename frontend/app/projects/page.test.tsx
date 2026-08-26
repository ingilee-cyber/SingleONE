import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProjectsPage from "./page";
import * as projectApi from "@/lib/projectApi";
import { useAdvertiserStore } from "@/lib/advertiserStore";

vi.mock("@/lib/projectApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/projectApi")>("@/lib/projectApi");
  return {
    ...actual,
    listProjects: vi.fn(),
    createProject: vi.fn(),
    updateProject: vi.fn(),
    deleteProject: vi.fn(),
    searchCampaigns: vi.fn(),
  };
});

const normalProject: projectApi.Project = {
  projectId: 1,
  advertiserId: "adv-1",
  projectName: "메타구글비교",
  systemDefault: false,
  referenceOnly: false,
  campaigns: [
    { media: "META", campaignId: "camp-meta", campaignName: "메타캠페인" },
    { media: "GOOGLE", campaignId: "camp-google", campaignName: "구글캠페인" },
  ],
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

const defaultProject: projectApi.Project = {
  ...normalProject,
  projectId: 2,
  projectName: "전체 캠페인",
  systemDefault: true,
  referenceOnly: true,
};

const campaignOptions: projectApi.CampaignOption[] = [
  { media: "META", campaignId: "camp-meta", campaignName: "메타캠페인" },
  { media: "GOOGLE", campaignId: "camp-google", campaignName: "구글캠페인" },
];

async function selectAdvertiser() {
  render(<ProjectsPage />);
  await screen.findByText("메타구글비교");
}

describe("ProjectsPage", () => {
  beforeEach(() => {
    useAdvertiserStore.setState({
      advertisers: [{ advertiserId: "adv-1", advertiserName: "adv-1" }],
      selectedAdvertiserId: "adv-1",
      loading: false,
      error: null,
      loaded: true,
    });
    vi.mocked(projectApi.listProjects).mockResolvedValue([normalProject, defaultProject]);
    vi.mocked(projectApi.searchCampaigns).mockResolvedValue(campaignOptions);
    vi.mocked(projectApi.createProject).mockResolvedValue(normalProject);
    vi.mocked(projectApi.updateProject).mockResolvedValue(normalProject);
    vi.mocked(projectApi.deleteProject).mockResolvedValue(undefined as never);
  });

  it("renders the project list with media chips, campaign count, and system-default badge", async () => {
    await selectAdvertiser();

    expect(screen.getAllByText("META").length).toBeGreaterThan(0);
    expect(screen.getByText("참고용 비교(전체 캠페인)")).toBeInTheDocument();
    // 시스템 기본 프로젝트에는 수정/삭제 버튼이 없어야 한다(PRD 5.2, AC-21).
    expect(screen.getAllByRole("button", { name: "수정" })).toHaveLength(1);
  });

  it("disables save until at least two distinct media are selected, then creates the project", async () => {
    await selectAdvertiser();
    fireEvent.click(screen.getByRole("button", { name: "새 프로젝트" }));
    await screen.findByText("[META] 메타캠페인 (camp-meta)");

    fireEvent.change(screen.getByRole("textbox", { name: "프로젝트명" }), { target: { value: "새프로젝트" } });
    const saveButton = screen.getByRole("button", { name: "저장" });
    expect(saveButton).toBeDisabled();

    fireEvent.click(screen.getByRole("checkbox", { name: /메타캠페인/ }));
    expect(saveButton).toBeDisabled();
    fireEvent.click(screen.getByRole("checkbox", { name: /구글캠페인/ }));
    expect(saveButton).not.toBeDisabled();

    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(projectApi.createProject).toHaveBeenCalledWith("adv-1", {
        projectName: "새프로젝트",
        campaigns: [
          { media: "META", campaignId: "camp-meta" },
          { media: "GOOGLE", campaignId: "camp-google" },
        ],
      });
    });
  });

  it("pre-fills the edit dialog and updates the existing project", async () => {
    await selectAdvertiser();
    fireEvent.click(screen.getByRole("button", { name: "수정" }));

    const nameInput = await screen.findByRole("textbox", { name: "프로젝트명" });
    expect(nameInput).toHaveValue("메타구글비교");

    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(projectApi.updateProject).toHaveBeenCalledWith(1, {
        projectName: "메타구글비교",
        campaigns: [
          { media: "META", campaignId: "camp-meta" },
          { media: "GOOGLE", campaignId: "camp-google" },
        ],
      });
    });
  });

  it("shows a confirmation dialog before deleting a project", async () => {
    await selectAdvertiser();
    fireEvent.click(screen.getByRole("button", { name: "삭제" }));

    await screen.findByText(/삭제하시겠습니까/);
    const dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(projectApi.deleteProject).toHaveBeenCalledWith(1);
    });
  });
});
