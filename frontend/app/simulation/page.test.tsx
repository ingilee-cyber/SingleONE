import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SimulationPage from "./page";
import { useSimulationStore } from "./simulationStore";
import { useAdvertiserStore } from "@/lib/advertiserStore";
import * as projectApi from "@/lib/projectApi";
import * as simulationApi from "@/lib/simulationApi";
import type { SimulationResult } from "@/lib/simulationApi";

const mockSearchParams = vi.fn(() => new URLSearchParams());
vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams(),
}));

vi.mock("@/app/dashboard/EChart", () => ({
  default: () => <div>echart-stub</div>,
}));

vi.mock("@/lib/projectApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/projectApi")>("@/lib/projectApi");
  return { ...actual, listProjects: vi.fn() };
});

vi.mock("@/lib/simulationApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/simulationApi")>("@/lib/simulationApi");
  return { ...actual, postSimulation: vi.fn() };
});

const userProject: projectApi.Project = {
  projectId: 1,
  advertiserId: "adv-1",
  projectName: "사용자 프로젝트",
  systemDefault: false,
  referenceOnly: false,
  campaigns: [{ media: "META", campaignId: "camp-meta", campaignName: "메타캠페인" }],
  createdAt: null,
  updatedAt: null,
};

const systemProject: projectApi.Project = {
  ...userProject,
  projectId: 2,
  projectName: "전체 캠페인",
  systemDefault: true,
  referenceOnly: true,
};

const result: SimulationResult = {
  mediaResults: [
    {
      media: "META",
      inputBudget: 2000000,
      weeklyBudget: 1000000,
      convertedCurrentBudget: 5000000,
      convertedCurrentWeeklyBudget: 2500000,
      confidence: "HIGH",
      predictedPurchases: 110,
      predictedRevenue: 2200000,
      predictedCpa: 18000,
      predictedRoas: 110,
      historicalMinWeeklyCost: 700000,
      historicalMaxWeeklyCost: 2660000,
      curvePoints: [{ weeklyCost: 1000000, predictedPurchases: 50, predictedRevenue: 900000 }],
      notes: ["효율 감소 관찰"],
    },
  ],
  totalBudget: 2000000,
  totalAvailable: true,
  totalPredictedPurchases: 110,
  totalPredictedRevenue: 2200000,
  totalPredictedCpa: 18000,
  totalPredictedRoas: 110,
  disclaimer: "예상 성과는 과거 운영 데이터를 기반으로 한 시뮬레이션 값이며 실제 광고 성과를 보장하지 않습니다.",
};

describe("SimulationPage", () => {
  beforeEach(() => {
    useSimulationStore.setState({
      projectId: "",
      baseFrom: "",
      baseTo: "",
      simFrom: "",
      simTo: "",
      mediaBudgets: {},
      result: null,
      loading: false,
      error: null,
    });
    useAdvertiserStore.setState({
      advertisers: [{ advertiserId: "adv-1", advertiserName: "adv-1" }],
      selectedAdvertiserId: "adv-1",
      loading: false,
      error: null,
      loaded: true,
    });
    mockSearchParams.mockReturnValue(new URLSearchParams());
    vi.mocked(projectApi.listProjects).mockResolvedValue([userProject, systemProject]);
    vi.mocked(simulationApi.postSimulation).mockResolvedValue(result);
  });

  it("excludes the system default project from the selectable list", async () => {
    render(<SimulationPage />);

    await screen.findByLabelText("META");
    expect(screen.queryByText("전체 캠페인")).not.toBeInTheDocument();
  });

  it("runs the simulation and shows the media result and disclaimer", async () => {
    render(<SimulationPage />);
    await screen.findByLabelText("META");

    fireEvent.change(screen.getByLabelText("META"), { target: { value: "2000000" } });
    fireEvent.click(screen.getByRole("button", { name: "시뮬레이션 실행" }));

    expect(await screen.findByText("높음")).toBeInTheDocument();
    expect(screen.getByText(/예상 성과는 과거 운영 데이터를 기반으로 한 시뮬레이션 값이며/)).toBeInTheDocument();
  });

  it("never renders recommendation or optimization wording", () => {
    render(<SimulationPage />);
    expect(screen.queryByText(/추천 예산|증액 추천|감액 추천|최적 예산|구매 최대화|매출 최대화/)).not.toBeInTheDocument();
  });

  // AC-43 보강: 결과가 채워진 뒤(백엔드 notes 등 자유 텍스트가 실제로 렌더링된 상태)에도 금지 표현이 없어야 한다.
  it("AC-43: still shows no recommendation/optimization wording after a simulation result is rendered", async () => {
    render(<SimulationPage />);
    await screen.findByLabelText("META");

    fireEvent.change(screen.getByLabelText("META"), { target: { value: "2000000" } });
    fireEvent.click(screen.getByRole("button", { name: "시뮬레이션 실행" }));

    expect(await screen.findByText("높음")).toBeInTheDocument();
    expect(screen.getByText("효율 감소 관찰")).toBeInTheDocument(); // notes가 실제로 렌더링됐음을 확인
    expect(screen.queryByText(/추천 예산|증액 추천|감액 추천|최적 예산|구매 최대화|매출 최대화/)).not.toBeInTheDocument();
  });

  // AC-50/AC-52: 신뢰도 낮음과 전체 KPI 산출 불가 상태도 실제로 렌더링되는지 확인한다.
  it("AC-50/AC-52: renders LOW confidence and '산출 불가' when the total is unavailable", async () => {
    vi.mocked(simulationApi.postSimulation).mockResolvedValue({
      ...result,
      mediaResults: [{ ...result.mediaResults[0], confidence: "LOW", notes: ["과거 운영 범위 초과", "포화구간 진입 가능성"] }],
      totalAvailable: false,
      totalPredictedPurchases: null,
      totalPredictedRevenue: null,
      totalPredictedCpa: null,
      totalPredictedRoas: null,
    });

    render(<SimulationPage />);
    await screen.findByLabelText("META");
    fireEvent.change(screen.getByLabelText("META"), { target: { value: "6000000" } });
    fireEvent.click(screen.getByRole("button", { name: "시뮬레이션 실행" }));

    expect(await screen.findByText("낮음")).toBeInTheDocument();
    expect(screen.getByText(/산출 불가/)).toBeInTheDocument();
  });

  // AC-53: Simulation 상태는 DB/LocalStorage에 남지 않고 Zustand 메모리에만 있어야 한다.
  it("AC-53: never writes simulation input/result to localStorage or sessionStorage", async () => {
    render(<SimulationPage />);
    await screen.findByLabelText("META");
    fireEvent.change(screen.getByLabelText("META"), { target: { value: "2000000" } });
    fireEvent.click(screen.getByRole("button", { name: "시뮬레이션 실행" }));
    await screen.findByText("높음");

    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });
});
