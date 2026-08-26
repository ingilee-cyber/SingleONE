import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DashboardPage from "./page";
import * as dashboardApi from "@/lib/dashboardApi";
import * as projectApi from "@/lib/projectApi";
import { useAdvertiserStore } from "@/lib/advertiserStore";

const push = vi.fn();
const mockSearchParams = vi.fn(() => new URLSearchParams());
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => mockSearchParams(),
}));

vi.mock("./EChart", () => ({
  default: ({ onClickCategory }: { onClickCategory?: (name: string) => void }) => (
    <button onClick={() => onClickCategory?.("GOOGLE")}>echart-stub</button>
  ),
}));

vi.mock("@/lib/dashboardApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/dashboardApi")>("@/lib/dashboardApi");
  return { ...actual, getDashboard: vi.fn() };
});

vi.mock("@/lib/projectApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/projectApi")>("@/lib/projectApi");
  return { ...actual, listProjects: vi.fn() };
});

const project: projectApi.Project = {
  projectId: 1,
  advertiserId: "adv-1",
  projectName: "테스트 프로젝트",
  systemDefault: false,
  referenceOnly: false,
  campaigns: [
    { media: "META", campaignId: "camp-meta", campaignName: "메타캠페인" },
    { media: "GOOGLE", campaignId: "camp-google", campaignName: "구글캠페인" },
  ],
  createdAt: null,
  updatedAt: null,
};

const emptyProject: projectApi.Project = { ...project, projectId: 2, projectName: "빈 프로젝트", campaigns: [] };

function mediaResult(media: projectApi.Media, indexScore: number): dashboardApi.MediaIndexResult {
  return {
    media,
    status: "VALID",
    rawTotals: { media, impressions: 500000, clicks: 10000, cost: 2500000, rawPurchases: 50, rawRevenue: 10000000, operatingDays: 10 },
    rawPerformance: { cpa: 50000, roas: 400 },
    singleOnePerformance: { media, singleOnePurchases: 32.5, singleOneRevenue: 6500000, cpa: 76923, roas: 260 },
    components: { exposureIndex: 90, clickIndex: 95, purchaseIndex: 80, revenueIndex: 85 },
    indexScore,
  };
}

function mediaResultWithStatus(media: projectApi.Media, status: dashboardApi.IndexStatus): dashboardApi.MediaIndexResult {
  return {
    media,
    status,
    rawTotals: { media, impressions: 500000, clicks: 10000, cost: 2500000, rawPurchases: 50, rawRevenue: 10000000, operatingDays: 10 },
    rawPerformance: { cpa: 50000, roas: 400 },
    singleOnePerformance: { media, singleOnePurchases: 32.5, singleOneRevenue: 6500000, cpa: 76923, roas: 260 },
    components: null,
    indexScore: null,
  };
}

const totals: dashboardApi.ProjectTotals = {
  impressions: 1000000,
  clicks: 20000,
  cost: 5000000,
  rawPurchases: 100,
  rawRevenue: 20000000,
  rawRoas: 400,
  singleOnePurchases: 65,
  singleOneRevenue: 13000000,
  singleOneRoas: 260,
};

const dashboardResponse: dashboardApi.DashboardResponse = {
  current: [mediaResult("GOOGLE", 113), mediaResult("META", 87)],
  currentTotals: totals,
  previous: [mediaResult("GOOGLE", 100), mediaResult("META", 100)],
  previousTotals: totals,
  rolling: [{ date: "2026-06-14", mediaResults: [mediaResult("GOOGLE", 113), mediaResult("META", 87)] }],
};

describe("DashboardPage", () => {
  beforeEach(() => {
    window.localStorage.clear();
    push.mockClear();
    mockSearchParams.mockReturnValue(new URLSearchParams());
    useAdvertiserStore.setState({
      advertisers: [{ advertiserId: "adv-1", advertiserName: "adv-1" }],
      selectedAdvertiserId: "adv-1",
      loading: false,
      error: null,
      loaded: true,
    });
    vi.mocked(projectApi.listProjects).mockResolvedValue([project]);
    vi.mocked(dashboardApi.getDashboard).mockResolvedValue(dashboardResponse);
  });

  it("shows a prompt when no advertiser is registered", () => {
    useAdvertiserStore.setState({ advertisers: [], selectedAdvertiserId: "", loaded: true });
    render(<DashboardPage />);
    expect(screen.getByText("등록된 광고주가 없습니다. 데이터 관리에서 먼저 데이터를 업로드하세요.")).toBeInTheDocument();
  });

  it("loads the project, fetches the dashboard, and renders KPI cards and the performance table", async () => {
    render(<DashboardPage />);

    await waitFor(() => {
      expect(dashboardApi.getDashboard).toHaveBeenCalledWith(1, expect.any(String), expect.any(String));
    });

    expect(await screen.findByText("5,000,000")).toBeInTheDocument(); // Cost 카드
    expect(screen.getAllByText("GOOGLE").length).toBeGreaterThan(0);
    expect(screen.getAllByText("META").length).toBeGreaterThan(0);
  });

  it("shows an error alert when the dashboard request fails", async () => {
    vi.mocked(dashboardApi.getDashboard).mockRejectedValue(new Error("network error"));
    render(<DashboardPage />);

    expect(await screen.findByText("Dashboard 데이터를 불러오지 못했습니다.")).toBeInTheDocument();
  });

  it("shows a warning when the selected project has no campaigns", async () => {
    vi.mocked(projectApi.listProjects).mockResolvedValue([emptyProject]);
    render(<DashboardPage />);

    expect(await screen.findByText("선택한 프로젝트에 포함된 캠페인이 없습니다.")).toBeInTheDocument();
  });

  it("hides a KPI card when toggled off and persists the choice to localStorage", async () => {
    render(<DashboardPage />);
    await screen.findByText("5,000,000");

    const kpiCards = screen.getByTestId("kpi-cards");
    expect(within(kpiCards).getByText("Impressions")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "표시 항목" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Impressions" }));

    await waitFor(() => {
      const stored = JSON.parse(window.localStorage.getItem("singleone.dashboard.kpiVisibility") ?? "[]");
      expect(stored).not.toContain("impressions");
    });
    expect(within(kpiCards).queryByText("Impressions")).not.toBeInTheDocument();
  });

  it("navigates to the media detail stub with the current filter context on media click", async () => {
    render(<DashboardPage />);
    await screen.findByText("5,000,000");

    fireEvent.click(screen.getAllByRole("button", { name: "echart-stub" })[0]);

    expect(push).toHaveBeenCalledTimes(1);
    const url = push.mock.calls[0][0] as string;
    expect(url).toContain("/dashboard/media/GOOGLE?");
    expect(url).toContain("projectId=1");
    expect(url).toContain("comparePrevious=true");
  });

  // AC-23: 상세 화면에서 Breadcrumb으로 돌아왔을 때 프로젝트/기간/이전 기간 비교 상태가
  // 유지되어야 한다(광고주는 전역 Header 선택값이 Source of Truth라 URL로 복원하지 않는다).
  it("restores project/period/comparePrevious from the URL when returning via breadcrumb", async () => {
    mockSearchParams.mockReturnValue(
      new URLSearchParams({
        projectId: "1",
        from: "2026-06-08",
        to: "2026-06-14",
        comparePrevious: "false",
      }),
    );

    render(<DashboardPage />);

    await waitFor(() => {
      expect(dashboardApi.getDashboard).toHaveBeenCalledWith(1, "2026-06-08", "2026-06-14");
    });
    expect(screen.getByRole("button", { name: "직접 설정" })).toHaveAttribute("aria-pressed", "true");
    expect(await screen.findByText("5,000,000")).toBeInTheDocument();
  });

  // AC-01: Dashboard 기본 진입 시 기간은 최근 30일, 이전 기간 비교는 ON이어야 한다.
  it("AC-01: defaults to a 30-day period with comparePrevious ON when opened with no query string", async () => {
    // 실제 "오늘"을 기준으로 최근 30일(오늘 포함) 기대값을 계산한다(fake timer는 RTL의 waitFor
    // 폴링과 충돌해 테스트가 멈추므로 사용하지 않는다).
    const toDate = new Date();
    const fromDate = new Date(toDate);
    fromDate.setDate(fromDate.getDate() - 29);
    const format = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

    render(<DashboardPage />);
    expect(screen.getByRole("button", { name: "최근 30일" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("switch", { name: "이전 기간 비교" })).toBeChecked();

    await waitFor(() => {
      expect(dashboardApi.getDashboard).toHaveBeenCalledWith(1, format(fromDate), format(toDate));
    });
  });

  // AC-05/06/08/09: Index 상태별 라벨이 그대로 표시돼야 한다.
  it("shows the correct Korean label for each non-VALID index status", async () => {
    vi.mocked(dashboardApi.getDashboard).mockResolvedValue({
      ...dashboardResponse,
      current: [
        mediaResultWithStatus("GOOGLE", "INSUFFICIENT_DATA"),
        mediaResultWithStatus("META", "MISSING_REQUIRED_DATA"),
        mediaResultWithStatus("TIKTOK", "COMPARISON_MEDIA_INSUFFICIENT"),
      ],
    });

    render(<DashboardPage />);

    expect(await screen.findByText("데이터 부족")).toBeInTheDocument();
    expect(screen.getByText("필수 데이터 누락")).toBeInTheDocument();
    expect(screen.getByText("비교 가능한 매체 부족")).toBeInTheDocument();
  });
});
