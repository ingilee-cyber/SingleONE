import { create } from "zustand";
import { postSimulation, type SimulationResult } from "@/lib/simulationApi";
import type { Media } from "@/lib/projectApi";

/**
 * PRD 10.9/AC-53: Simulation 입력/결과는 DB/LocalStorage에 저장하지 않고 현재 페이지 세션의
 * 메모리(Zustand)에만 둔다. persist 미들웨어를 쓰지 않으므로 새로고침하면 그대로 초기화된다.
 */
interface SimulationState {
  projectId: number | "";
  baseFrom: string;
  baseTo: string;
  simFrom: string;
  simTo: string;
  mediaBudgets: Partial<Record<Media, string>>;
  result: SimulationResult | null;
  loading: boolean;
  error: string | null;
  setProjectId: (value: number | "") => void;
  setBaseFrom: (value: string) => void;
  setBaseTo: (value: string) => void;
  setSimFrom: (value: string) => void;
  setSimTo: (value: string) => void;
  setMediaBudget: (media: Media, value: string) => void;
  runSimulation: () => Promise<void>;
}

export const useSimulationStore = create<SimulationState>((set, get) => ({
  projectId: "",
  baseFrom: "",
  baseTo: "",
  simFrom: "",
  simTo: "",
  mediaBudgets: {},
  result: null,
  loading: false,
  error: null,

  setProjectId: (value) => set({ projectId: value, mediaBudgets: {}, result: null }),
  setBaseFrom: (value) => set({ baseFrom: value }),
  setBaseTo: (value) => set({ baseTo: value }),
  setSimFrom: (value) => set({ simFrom: value }),
  setSimTo: (value) => set({ simTo: value }),
  setMediaBudget: (media, value) => set((state) => ({ mediaBudgets: { ...state.mediaBudgets, [media]: value } })),

  runSimulation: async () => {
    const state = get();
    if (!state.projectId || !state.baseFrom || !state.baseTo || !state.simFrom || !state.simTo) {
      return;
    }
    set({ loading: true, error: null });
    const mediaBudgets: Partial<Record<Media, number>> = {};
    for (const media of Object.keys(state.mediaBudgets) as Media[]) {
      mediaBudgets[media] = Number(state.mediaBudgets[media] || 0);
    }
    try {
      const result = await postSimulation(Number(state.projectId), {
        baseFrom: state.baseFrom,
        baseTo: state.baseTo,
        simFrom: state.simFrom,
        simTo: state.simTo,
        mediaBudgets,
      });
      set({ result, loading: false });
    } catch {
      set({ error: "시뮬레이션 결과를 계산하지 못했습니다.", loading: false, result: null });
    }
  },
}));
