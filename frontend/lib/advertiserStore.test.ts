import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAdvertiserStore } from "./advertiserStore";
import * as advertiserApi from "@/lib/advertiserApi";

vi.mock("@/lib/advertiserApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/advertiserApi")>("@/lib/advertiserApi");
  return { ...actual, listAdvertisers: vi.fn() };
});

function resetStore() {
  useAdvertiserStore.setState({
    advertisers: [],
    selectedAdvertiserId: "",
    loading: false,
    error: null,
    loaded: false,
  });
}

describe("useAdvertiserStore", () => {
  beforeEach(() => {
    resetStore();
    vi.clearAllMocks();
  });

  it("auto-selects the first advertiser after loading when nothing is selected yet", async () => {
    vi.mocked(advertiserApi.listAdvertisers).mockResolvedValue([
      { advertiserId: "abc-brand", advertiserName: "ABC Brand" },
      { advertiserId: "zzz-brand", advertiserName: "ZZZ Brand" },
    ]);

    await useAdvertiserStore.getState().loadAdvertisers();

    expect(useAdvertiserStore.getState().selectedAdvertiserId).toBe("abc-brand");
    expect(useAdvertiserStore.getState().advertisers).toHaveLength(2);
    expect(useAdvertiserStore.getState().loaded).toBe(true);
  });

  it("keeps the current selection if it still exists in the reloaded list", async () => {
    useAdvertiserStore.setState({ selectedAdvertiserId: "zzz-brand" });
    vi.mocked(advertiserApi.listAdvertisers).mockResolvedValue([
      { advertiserId: "abc-brand", advertiserName: "ABC Brand" },
      { advertiserId: "zzz-brand", advertiserName: "ZZZ Brand" },
    ]);

    await useAdvertiserStore.getState().loadAdvertisers();

    expect(useAdvertiserStore.getState().selectedAdvertiserId).toBe("zzz-brand");
  });

  it("falls back to the first advertiser when the previous selection no longer exists", async () => {
    useAdvertiserStore.setState({ selectedAdvertiserId: "deleted-brand" });
    vi.mocked(advertiserApi.listAdvertisers).mockResolvedValue([
      { advertiserId: "abc-brand", advertiserName: "ABC Brand" },
    ]);

    await useAdvertiserStore.getState().loadAdvertisers();

    expect(useAdvertiserStore.getState().selectedAdvertiserId).toBe("abc-brand");
  });

  it("does not manufacture an advertiser when none has uploaded data", async () => {
    vi.mocked(advertiserApi.listAdvertisers).mockResolvedValue([]);

    await useAdvertiserStore.getState().loadAdvertisers();

    expect(useAdvertiserStore.getState().advertisers).toEqual([]);
    expect(useAdvertiserStore.getState().selectedAdvertiserId).toBe("");
  });

  it("sets an error message when the request fails", async () => {
    vi.mocked(advertiserApi.listAdvertisers).mockRejectedValue(new Error("network error"));

    await useAdvertiserStore.getState().loadAdvertisers();

    expect(useAdvertiserStore.getState().error).toBe("광고주 목록을 불러오지 못했습니다.");
    expect(useAdvertiserStore.getState().loading).toBe(false);
  });

  it("setSelectedAdvertiserId updates the selection directly", () => {
    useAdvertiserStore.getState().setSelectedAdvertiserId("abc-brand");
    expect(useAdvertiserStore.getState().selectedAdvertiserId).toBe("abc-brand");
  });
});
