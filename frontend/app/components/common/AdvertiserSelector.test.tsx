import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdvertiserSelector from "./AdvertiserSelector";
import { useAdvertiserStore } from "@/lib/advertiserStore";
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

describe("AdvertiserSelector", () => {
  beforeEach(() => {
    resetStore();
    vi.clearAllMocks();
  });

  it("loads advertisers on mount and lets the user pick one", async () => {
    vi.mocked(advertiserApi.listAdvertisers).mockResolvedValue([
      { advertiserId: "abc-brand", advertiserName: "ABC Brand" },
      { advertiserId: "zzz-brand", advertiserName: "ZZZ Brand" },
    ]);

    render(<AdvertiserSelector />);

    const input = await screen.findByRole("combobox", { name: "광고주" });
    expect(input).toHaveValue("abc-brand"); // 최초 진입 시 첫 번째 광고주 자동 선택

    fireEvent.mouseDown(input);
    fireEvent.click(await screen.findByRole("option", { name: "zzz-brand" }));

    expect(useAdvertiserStore.getState().selectedAdvertiserId).toBe("zzz-brand");
  });

  it("shows a message when no advertiser has uploaded data", async () => {
    vi.mocked(advertiserApi.listAdvertisers).mockResolvedValue([]);

    render(<AdvertiserSelector />);
    const input = await screen.findByRole("combobox", { name: "광고주" });
    fireEvent.mouseDown(input);

    expect(await screen.findByText("등록된 광고주 없음")).toBeInTheDocument();
  });

  it("shows an error message when the advertiser list fails to load", async () => {
    vi.mocked(advertiserApi.listAdvertisers).mockRejectedValue(new Error("network error"));

    render(<AdvertiserSelector />);

    expect(await screen.findByText("광고주 목록을 불러오지 못했습니다.")).toBeInTheDocument();
  });
});
