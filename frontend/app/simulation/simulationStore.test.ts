import { beforeEach, describe, expect, it, vi } from "vitest";
import { useSimulationStore } from "./simulationStore";
import * as simulationApi from "@/lib/simulationApi";
import type { SimulationResult } from "@/lib/simulationApi";

vi.mock("@/lib/simulationApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/simulationApi")>("@/lib/simulationApi");
  return { ...actual, postSimulation: vi.fn() };
});

const result: SimulationResult = {
  mediaResults: [],
  totalBudget: 100,
  totalAvailable: true,
  totalPredictedPurchases: 1,
  totalPredictedRevenue: 2,
  totalPredictedCpa: 3,
  totalPredictedRoas: 4,
  disclaimer: "예상 성과는 과거 운영 데이터를 기반으로 한 시뮬레이션 값이며 실제 광고 성과를 보장하지 않습니다.",
};

function resetStore() {
  useSimulationStore.setState({
    advertiserId: "",
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
}

describe("useSimulationStore", () => {
  beforeEach(() => {
    resetStore();
    vi.clearAllMocks();
  });

  it("clears the project selection and any prior result when the advertiser changes", () => {
    useSimulationStore.getState().setProjectId(5);
    useSimulationStore.setState({ result });

    useSimulationStore.getState().setAdvertiserId("adv-2");

    expect(useSimulationStore.getState().advertiserId).toBe("adv-2");
    expect(useSimulationStore.getState().projectId).toBe("");
    expect(useSimulationStore.getState().result).toBeNull();
  });

  it("does not call the API until project and both periods are set", async () => {
    await useSimulationStore.getState().runSimulation();
    expect(simulationApi.postSimulation).not.toHaveBeenCalled();
  });

  it("converts media budget strings to numbers and stores the result on success", async () => {
    vi.mocked(simulationApi.postSimulation).mockResolvedValue(result);
    useSimulationStore.setState({
      projectId: 7,
      baseFrom: "2026-07-01",
      baseTo: "2026-07-07",
      simFrom: "2026-08-01",
      simTo: "2026-08-14",
      mediaBudgets: { META: "2000000", GOOGLE: "" },
    });

    await useSimulationStore.getState().runSimulation();

    expect(simulationApi.postSimulation).toHaveBeenCalledWith(7, {
      baseFrom: "2026-07-01",
      baseTo: "2026-07-07",
      simFrom: "2026-08-01",
      simTo: "2026-08-14",
      mediaBudgets: { META: 2000000, GOOGLE: 0 },
    });
    expect(useSimulationStore.getState().result).toEqual(result);
    expect(useSimulationStore.getState().loading).toBe(false);
  });

  it("sets an error message and clears the result when the request fails", async () => {
    vi.mocked(simulationApi.postSimulation).mockRejectedValue(new Error("fail"));
    useSimulationStore.setState({
      projectId: 7,
      baseFrom: "2026-07-01",
      baseTo: "2026-07-07",
      simFrom: "2026-08-01",
      simTo: "2026-08-14",
    });

    await useSimulationStore.getState().runSimulation();

    expect(useSimulationStore.getState().error).toBe("시뮬레이션 결과를 계산하지 못했습니다.");
    expect(useSimulationStore.getState().result).toBeNull();
  });
});
