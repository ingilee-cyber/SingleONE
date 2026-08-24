import type { EChartsCoreOption } from "echarts";
import type { MediaSimulationResult } from "@/lib/simulationApi";

/**
 * PRD 10.8: 한계 효율 곡선에 현재/입력 예산, 과거 운영 범위, 150% 외삽 한계를 함께 표시한다.
 * 순수 함수로 분리해 단위 테스트한다.
 */
export function buildMarginalEfficiencyOption(result: MediaSimulationResult): EChartsCoreOption {
  const data = result.curvePoints.map((p) => [p.weeklyCost, p.predictedPurchases]);
  const extrapolationLimit = result.historicalMaxWeeklyCost != null ? result.historicalMaxWeeklyCost * 1.5 : null;

  const markLineData: Record<string, unknown>[] = [];
  if (result.convertedCurrentWeeklyBudget != null) {
    markLineData.push({ xAxis: result.convertedCurrentWeeklyBudget, name: "환산 현재 운영(주간)", label: { formatter: "환산 현재 운영" } });
  }
  markLineData.push({ xAxis: result.weeklyBudget, name: "입력 예산(주간 환산)", label: { formatter: "입력 예산" } });
  if (extrapolationLimit != null) {
    markLineData.push({ xAxis: extrapolationLimit, name: "150% 외삽 한계", label: { formatter: "150% 한계" } });
  }

  const markAreaData =
    result.historicalMinWeeklyCost != null && result.historicalMaxWeeklyCost != null
      ? [[{ xAxis: result.historicalMinWeeklyCost, name: "과거 운영 범위" }, { xAxis: result.historicalMaxWeeklyCost }]]
      : [];

  return {
    xAxis: { type: "value", name: "주간 예산(원)" },
    yAxis: { type: "value", name: "예상 SingleONE 구매" },
    tooltip: { trigger: "axis" },
    series: [
      {
        type: "line",
        data,
        smooth: true,
        showSymbol: false,
        markLine: { symbol: "none", data: markLineData },
        markArea: { data: markAreaData },
      },
    ],
  } as EChartsCoreOption;
}
