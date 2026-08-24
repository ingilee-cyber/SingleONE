import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import JourneySummaryPlaceholder from "./JourneySummaryPlaceholder";
import * as journeyApi from "@/lib/journeyApi";
import type { JourneyAnalysisResult } from "@/lib/journeyApi";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/lib/journeyApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/journeyApi")>("@/lib/journeyApi");
  return { ...actual, getJourneyAnalysis: vi.fn() };
});

const result: JourneyAnalysisResult = {
  topPaths: [{ channels: ["META", "GOOGLE"], purchaseCount: 2, purchaseRevenue: 200000 }],
  attribution: [{ channel: "GOOGLE", attributedPurchases: 1.5, attributedRevenue: 150000, sharePercent: 60 }],
  channelPairs: [{ channelA: "META", channelB: "GOOGLE", journeyCount: 2, purchaseRevenue: 200000, sharePercent: 100 }],
  totalPurchaseJourneys: 2,
  attributedJourneyCount: 2,
  totalPurchaseRevenue: 200000,
};

describe("JourneySummaryPlaceholder", () => {
  beforeEach(() => {
    push.mockClear();
  });

  it("shows the top path/channel/pair and navigates to /journey with the current filter context", async () => {
    vi.mocked(journeyApi.getJourneyAnalysis).mockResolvedValue(result);
    render(<JourneySummaryPlaceholder advertiserId="adv-1" projectId={1} from="2026-07-01" to="2026-07-07" />);

    expect(await screen.findByText(/META → GOOGLE → 구매/)).toBeInTheDocument();
    expect(screen.getByText(/GOOGLE \(기여 구매 2건\)/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "상세 분석으로 이동" }));

    expect(push).toHaveBeenCalledTimes(1);
    const url = push.mock.calls[0][0] as string;
    expect(url).toContain("/journey?");
    expect(url).toContain("advertiserId=adv-1");
    expect(url).toContain("projectId=1");
  });

  it("shows an info message when there are no analyzable journeys", async () => {
    vi.mocked(journeyApi.getJourneyAnalysis).mockResolvedValue({
      ...result,
      topPaths: [],
      attribution: [],
      channelPairs: [],
      attributedJourneyCount: 0,
    });
    render(<JourneySummaryPlaceholder advertiserId="adv-1" projectId={1} from="2026-07-01" to="2026-07-07" />);

    expect(await screen.findByText("선택한 기간에 분석 가능한 Journey 이벤트가 없습니다.")).toBeInTheDocument();
  });

  it("shows an error alert when the request fails", async () => {
    vi.mocked(journeyApi.getJourneyAnalysis).mockRejectedValue(new Error("fail"));
    render(<JourneySummaryPlaceholder advertiserId="adv-1" projectId={1} from="2026-07-01" to="2026-07-07" />);

    expect(await screen.findByText("Journey 데이터를 불러오지 못했습니다.")).toBeInTheDocument();
  });
});
