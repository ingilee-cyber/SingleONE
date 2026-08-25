import type { EChartsCoreOption } from "echarts";
import type { MediaSimulationResult } from "@/lib/simulationApi";
import { mediaColor } from "@/lib/mediaColors";

/**
 * PRD 10.8: 한계 효율 곡선에 현재/입력 예산, 과거 운영 범위, 150% 외삽 한계를 함께 표시한다.
 * 순수 함수로 분리해 단위 테스트한다.
 *
 * 주석: 라벨 3개(환산 현재 운영/입력 예산/150% 한계)가 기본 위치(전부 차트 상단)에서 서로 겹치던
 * 실제 레이아웃 버그가 있었다. markLine 라벨마다 position을 start/middle/end top으로 다르게 줘서
 * 세로로 분산시키고, markArea 라벨은 하단에 배치해 분리한다. y축 이름도 상단 겹침을 피하려고
 * 세로 축 옆(middle, 90도 회전)으로 옮긴다. data/markArea.data 값 자체는 그대로다(테스트 보호).
 */
export function buildMarginalEfficiencyOption(result: MediaSimulationResult): EChartsCoreOption {
  const data = result.curvePoints.map((p) => [p.weeklyCost, p.predictedPurchases]);
  const extrapolationLimit = result.historicalMaxWeeklyCost != null ? result.historicalMaxWeeklyCost * 1.5 : null;
  const lineColor = mediaColor(result.media);

  const markLineData: Record<string, unknown>[] = [];
  if (result.convertedCurrentWeeklyBudget != null) {
    markLineData.push({
      xAxis: result.convertedCurrentWeeklyBudget,
      name: "환산 현재 운영(주간)",
      label: { formatter: "환산 현재 운영", position: "insideStartTop", rotate: 0 },
    });
  }
  markLineData.push({
    xAxis: result.weeklyBudget,
    name: "입력 예산(주간 환산)",
    label: { formatter: "입력 예산", position: "insideMiddleTop", rotate: 0 },
  });
  if (extrapolationLimit != null) {
    markLineData.push({
      xAxis: extrapolationLimit,
      name: "150% 외삽 한계",
      label: { formatter: "150% 한계", position: "insideEndTop", rotate: 0 },
    });
  }

  const markAreaData =
    result.historicalMinWeeklyCost != null && result.historicalMaxWeeklyCost != null
      ? [[{ xAxis: result.historicalMinWeeklyCost, name: "과거 운영 범위" }, { xAxis: result.historicalMaxWeeklyCost }]]
      : [];

  return {
    grid: { top: 56, right: 24 },
    xAxis: { type: "value", name: "주간 예산(원)" },
    yAxis: { type: "value", name: "예상 SingleONE 구매", nameLocation: "middle", nameGap: 40, nameRotate: 90 },
    tooltip: { trigger: "axis" },
    series: [
      {
        type: "line",
        data,
        smooth: true,
        showSymbol: false,
        itemStyle: { color: lineColor },
        lineStyle: { color: lineColor },
        markLine: { symbol: "none", data: markLineData },
        markArea: { data: markAreaData, label: { position: "insideTop", color: "#667085" }, itemStyle: { color: `${lineColor}1A` } },
      },
    ],
  } as EChartsCoreOption;
}
