import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import JourneyPage from "./page";
import * as journeyApi from "@/lib/journeyApi";
import * as projectApi from "@/lib/projectApi";
import type { JourneyAnalysisResult } from "@/lib/journeyApi";

const mockSearchParams = vi.fn(() => new URLSearchParams());
vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams(),
}));

vi.mock("@/app/dashboard/EChart", () => ({
  default: () => <div>echart-stub</div>,
}));

vi.mock("@/lib/journeyApi", async () => {
  const actual = await vi.importActual<typeof import("@/lib/journeyApi")>("@/lib/journeyApi");
  return { ...actual, getJourneyAnalysis: vi.fn() };
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
  campaigns: [{ media: "META", campaignId: "camp-meta", campaignName: "메타캠페인" }],
  createdAt: null,
  updatedAt: null,
};

const result: JourneyAnalysisResult = {
  topPaths: [{ channels: ["META", "GOOGLE"], purchaseCount: 2, purchaseRevenue: 200000 }],
  attribution: [{ channel: "GOOGLE", attributedPurchases: 1.5, attributedRevenue: 150000, sharePercent: 60 }],
  channelPairs: [{ channelA: "META", channelB: "GOOGLE", journeyCount: 2, purchaseRevenue: 200000, sharePercent: 100 }],
  totalPurchaseJourneys: 2,
  attributedJourneyCount: 2,
  totalPurchaseRevenue: 200000,
};

describe("JourneyPage", () => {
  beforeEach(() => {
    mockSearchParams.mockReturnValue(
      new URLSearchParams({ advertiserId: "adv-1", projectId: "1", from: "2026-07-01", to: "2026-07-07" }),
    );
    vi.mocked(projectApi.listProjects).mockResolvedValue([project]);
    vi.mocked(journeyApi.getJourneyAnalysis).mockResolvedValue(result);
  });

  it("restores filters from the URL and shows the 사용자 여정 tab by default", async () => {
    render(<JourneyPage />);

    expect(await screen.findByText("echart-stub")).toBeInTheDocument();
    expect(screen.getByText(/META → GOOGLE → 구매/)).toBeInTheDocument();
  });

  it("switches to the 채널별 전환 기여도 tab and shows the guidance banner and attribution rows", async () => {
    render(<JourneyPage />);
    await screen.findByText("echart-stub");

    fireEvent.click(screen.getByRole("tab", { name: "채널별 전환 기여도" }));

    expect(
      screen.getByText(/Journey & Attribution 분석은 이벤트 데이터를 기준으로 하며/),
    ).toBeInTheDocument();
    expect(screen.getByText("GOOGLE")).toBeInTheDocument();
    expect(screen.getByText("60.0%")).toBeInTheDocument();
  });

  it("switches to the 채널 페어 인사이트 tab and shows pair rows", async () => {
    render(<JourneyPage />);
    await screen.findByText("echart-stub");

    fireEvent.click(screen.getByRole("tab", { name: "채널 페어 인사이트" }));

    expect(screen.getByText("META + GOOGLE")).toBeInTheDocument();
    expect(screen.getByText("가장 많이 관찰된 채널 페어부터 정렬해 표시합니다.")).toBeInTheDocument();
  });

  // AC-42: Journey 탭 어디에도 "SingleONE 기여 구매"라는 표현을 쓰지 않는다.
  it("AC-42: never shows the phrase 'SingleONE 기여 구매' on any tab", async () => {
    render(<JourneyPage />);
    await screen.findByText("echart-stub");
    expect(screen.queryByText(/SingleONE 기여 구매/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "채널별 전환 기여도" }));
    expect(screen.queryByText(/SingleONE 기여 구매/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "채널 페어 인사이트" }));
    expect(screen.queryByText(/SingleONE 기여 구매/)).not.toBeInTheDocument();
  });
});
