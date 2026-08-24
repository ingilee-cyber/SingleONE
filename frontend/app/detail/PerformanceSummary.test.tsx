import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import PerformanceSummary from "./PerformanceSummary";
import type { PerformanceLike } from "./PerformanceSummary";
import type { MediaIndexResult } from "@/lib/dashboardApi";

const current: PerformanceLike = {
  rawTotals: { media: "META", impressions: 100000, clicks: 2000, cost: 1000000, rawPurchases: 50, rawRevenue: 5000000, operatingDays: 7 },
  rawPerformance: { cpa: 20000, roas: 500 },
  singleOnePerformance: { media: "META", singleOnePurchases: 32.5, singleOneRevenue: 3250000, cpa: 30769, roas: 325 },
};

const previous: PerformanceLike = {
  rawTotals: { media: "META", impressions: 80000, clicks: 1600, cost: 800000, rawPurchases: 40, rawRevenue: 4000000, operatingDays: 7 },
  rawPerformance: { cpa: 20000, roas: 500 },
  singleOnePerformance: { media: "META", singleOnePurchases: 26, singleOneRevenue: 2600000, cpa: 30769, roas: 325 },
};

function indexResult(score: number, status: MediaIndexResult["status"] = "VALID"): MediaIndexResult {
  return {
    media: "META",
    status,
    rawTotals: current.rawTotals,
    rawPerformance: current.rawPerformance,
    singleOnePerformance: current.singleOnePerformance,
    components: { exposureIndex: 100, clickIndex: 100, purchaseIndex: 100, revenueIndex: 100 },
    indexScore: status === "VALID" ? score : null,
  };
}

describe("PerformanceSummary", () => {
  it("renders current raw and SingleONE metrics with the conversion-basis tooltip", async () => {
    render(<PerformanceSummary current={current} />);

    expect(screen.getByText("1,000,000")).toBeInTheDocument(); // Cost는 원본만 표시
    expect(screen.getByText("원본 50")).toBeInTheDocument();
    expect(screen.getByText("SingleONE 33")).toBeInTheDocument(); // Math.round(32.5)
    expect(screen.getByText("원본 500.0%")).toBeInTheDocument();

    fireEvent.mouseOver(screen.getByText("ⓘ"));
    expect(await screen.findByText("자체 내부 전환 기준입니다.")).toBeInTheDocument();
  });

  it("does not render an index or previous-period section unless provided", () => {
    render(<PerformanceSummary current={current} />);
    expect(screen.queryByText("SingleONE Index")).not.toBeInTheDocument();
    expect(screen.queryByText("이전 기간")).not.toBeInTheDocument();
  });

  it("shows the previous-period block when previous is provided (campaign detail)", () => {
    render(<PerformanceSummary current={current} previous={previous} />);
    expect(screen.getByText("이전 기간")).toBeInTheDocument();
  });

  it("shows the index section with the delta from the previous period (media detail)", () => {
    render(<PerformanceSummary current={current} indexSection={{ current: indexResult(120), previous: indexResult(100) }} />);
    expect(screen.getByText("SingleONE Index")).toBeInTheDocument();
    expect(screen.getByText("120")).toBeInTheDocument();
    expect(screen.getByText(/이전 기간 100/)).toBeInTheDocument();
  });

  it("shows a data-shortage message when the previous index is unavailable", () => {
    render(<PerformanceSummary current={current} indexSection={{ current: indexResult(120), previous: indexResult(0, "MISSING_REQUIRED_DATA") }} />);
    expect(screen.getByText("이전 기간 데이터 부족")).toBeInTheDocument();
  });
});
