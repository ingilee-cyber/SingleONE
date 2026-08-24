import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MediaDetailPage from "./page";
import * as detailApi from "@/lib/detailApi";
import type { MediaDetailResponse } from "@/lib/detailApi";
import type { MediaIndexResult } from "@/lib/dashboardApi";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useParams: () => ({ media: "GOOGLE" }),
  useSearchParams: () =>
    new URLSearchParams({
      advertiserId: "adv-1",
      projectId: "1",
      from: "2026-07-01",
      to: "2026-07-07",
      comparePrevious: "true",
    }),
  useRouter: () => ({ push }),
}));

vi.mock("@/app/dashboard/EChart", () => ({
  default: () => <div>echart-stub</div>,
}));

vi.mock("@/lib/detailApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/detailApi")>("@/lib/detailApi");
  return { ...actual, getMediaDetail: vi.fn(), listCampaigns: vi.fn() };
});

function mediaIndexResult(score: number): MediaIndexResult {
  return {
    media: "GOOGLE",
    status: "VALID",
    rawTotals: { media: "GOOGLE", impressions: 100000, clicks: 2000, cost: 1000000, rawPurchases: 50, rawRevenue: 5000000, operatingDays: 7 },
    rawPerformance: { cpa: 20000, roas: 500 },
    singleOnePerformance: { media: "GOOGLE", singleOnePurchases: 34.5, singleOneRevenue: 3450000, cpa: 28985, roas: 345 },
    components: { exposureIndex: 110, clickIndex: 110, purchaseIndex: 110, revenueIndex: 110 },
    indexScore: score,
  };
}

const mediaDetailResponse: MediaDetailResponse = {
  current: mediaIndexResult(120),
  previous: mediaIndexResult(100),
  rolling: [{ date: "2026-07-07", mediaResults: [mediaIndexResult(120)] }],
};

const campaignPage: detailApi.EntityPage = {
  content: [
    {
      id: "camp-google",
      name: "구글캠페인",
      rawTotals: { media: "GOOGLE", impressions: 100000, clicks: 2000, cost: 1000000, rawPurchases: 50, rawRevenue: 5000000, operatingDays: 7 },
      rawPerformance: { cpa: 20000, roas: 500 },
      singleOnePerformance: { media: "GOOGLE", singleOnePurchases: 34.5, singleOneRevenue: 3450000, cpa: 28985, roas: 345 },
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 50,
};

describe("MediaDetailPage", () => {
  beforeEach(() => {
    push.mockClear();
    vi.mocked(detailApi.getMediaDetail).mockResolvedValue(mediaDetailResponse);
    vi.mocked(detailApi.listCampaigns).mockResolvedValue(campaignPage);
  });

  it("renders the index score and breadcrumb, then navigates to the campaign detail on row click", async () => {
    render(<MediaDetailPage />);

    expect(await screen.findByText("120")).toBeInTheDocument();
    expect(screen.getByText("매체: GOOGLE")).toBeInTheDocument();
    expect(await screen.findByText("구글캠페인")).toBeInTheDocument();

    fireEvent.click(screen.getByText("구글캠페인"));

    expect(push).toHaveBeenCalledTimes(1);
    const url = push.mock.calls[0][0] as string;
    expect(url).toContain("/dashboard/media/GOOGLE/campaigns/camp-google?");
    expect(url).toContain("advertiserId=adv-1");
    expect(url).toContain("projectId=1");
  });

  it("shows an error alert when the summary request fails", async () => {
    vi.mocked(detailApi.getMediaDetail).mockRejectedValue(new Error("fail"));
    render(<MediaDetailPage />);

    expect(await screen.findByText("매체 상세 정보를 불러오지 못했습니다.")).toBeInTheDocument();
  });
});
