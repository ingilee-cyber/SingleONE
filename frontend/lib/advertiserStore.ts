import { create } from "zustand";
import { listAdvertisers, type AdvertiserOption } from "@/lib/advertiserApi";

/**
 * 전역 광고주 선택 상태. Dashboard/프로젝트/Journey/Simulation 등 모든 화면이 이 store를
 * Source of Truth로 공유한다(각 화면이 advertiserId를 독립적으로 관리하지 않는다). 데이터
 * 관리(업로드) 화면만 예외로 자체 광고주 ID 입력을 그대로 쓴다(신규 광고주 온보딩 경로).
 */
interface AdvertiserState {
  advertisers: AdvertiserOption[];
  selectedAdvertiserId: string;
  loading: boolean;
  error: string | null;
  loaded: boolean;
  loadAdvertisers: () => Promise<void>;
  setSelectedAdvertiserId: (value: string) => void;
}

export const useAdvertiserStore = create<AdvertiserState>((set, get) => ({
  advertisers: [],
  selectedAdvertiserId: "",
  loading: false,
  error: null,
  loaded: false,

  loadAdvertisers: async () => {
    if (get().loading) {
      return;
    }
    set({ loading: true, error: null });
    try {
      const advertisers = await listAdvertisers();
      set((state) => ({
        advertisers,
        loading: false,
        loaded: true,
        // 아직 선택된 광고주가 없거나(최초 진입), 이전에 선택했던 광고주가 더 이상 목록에 없으면
        // 첫 번째 광고주를 자동 선택한다(전역 드롭다운만 있고 텍스트 입력이 없으므로, 자동 선택이
        // 없으면 화면이 계속 빈 상태로 남는다).
        selectedAdvertiserId: advertisers.some((a) => a.advertiserId === state.selectedAdvertiserId)
          ? state.selectedAdvertiserId
          : (advertisers[0]?.advertiserId ?? ""),
      }));
    } catch {
      set({ loading: false, error: "광고주 목록을 불러오지 못했습니다." });
    }
  },

  setSelectedAdvertiserId: (value) => set({ selectedAdvertiserId: value }),
}));
