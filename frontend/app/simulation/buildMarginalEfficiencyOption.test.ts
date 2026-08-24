import { describe, expect, it } from "vitest";
import { buildMarginalEfficiencyOption } from "./buildMarginalEfficiencyOption";
import type { MediaSimulationResult } from "@/lib/simulationApi";

const baseResult: MediaSimulationResult = {
  media: "META",
  inputBudget: 2000000,
  weeklyBudget: 1000000,
  convertedCurrentBudget: 5320000,
  convertedCurrentWeeklyBudget: 2660000,
  confidence: "HIGH",
  predictedPurchases: 110,
  predictedRevenue: 2200000,
  predictedCpa: 18000,
  predictedRoas: 110,
  historicalMinWeeklyCost: 700000,
  historicalMaxWeeklyCost: 2660000,
  curvePoints: [
    { weeklyCost: 100000, predictedPurchases: 10, predictedRevenue: 200000 },
    { weeklyCost: 1000000, predictedPurchases: 50, predictedRevenue: 900000 },
  ],
  notes: ["효율 감소 관찰"],
};

function series(option: ReturnType<typeof buildMarginalEfficiencyOption>) {
  // @ts-expect-error - test-only access into the loosely-typed EChartsCoreOption.
  return option.series[0];
}

describe("buildMarginalEfficiencyOption", () => {
  it("plots the curve points as (weeklyCost, predictedPurchases) pairs", () => {
    const s = series(buildMarginalEfficiencyOption(baseResult));
    expect(s.data).toEqual([
      [100000, 10],
      [1000000, 50],
    ]);
  });

  it("adds markLine entries for the input budget, converted current budget, and 150% limit", () => {
    const s = series(buildMarginalEfficiencyOption(baseResult));
    const xValues = s.markLine.data.map((d: { xAxis: number }) => d.xAxis);
    expect(xValues).toContain(1000000); // weeklyBudget
    expect(xValues).toContain(2660000); // convertedCurrentWeeklyBudget
    expect(xValues).toContain(2660000 * 1.5); // 150% extrapolation limit
  });

  it("adds a markArea band spanning the historical min/max weekly cost", () => {
    const s = series(buildMarginalEfficiencyOption(baseResult));
    expect(s.markArea.data).toEqual([[{ xAxis: 700000, name: "과거 운영 범위" }, { xAxis: 2660000 }]]);
  });

  it("omits historical range markers when the media has no historical data", () => {
    const noHistory: MediaSimulationResult = { ...baseResult, historicalMinWeeklyCost: null, historicalMaxWeeklyCost: null };
    const s = series(buildMarginalEfficiencyOption(noHistory));
    expect(s.markArea.data).toEqual([]);
    const xValues = s.markLine.data.map((d: { xAxis: number }) => d.xAxis);
    expect(xValues).not.toContain(2660000 * 1.5);
  });
});
