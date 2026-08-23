"use client";

import { useEffect, useRef } from "react";
import * as echarts from "echarts";

interface EChartProps {
  option: echarts.EChartsCoreOption;
  height?: number;
  onClickCategory?: (category: string) => void;
}

/** echarts-for-react가 없어 직접 만든 얇은 wrapper. Bar/Line 차트 공용으로 재사용한다. */
export default function EChart({ option, height = 320, onClickCategory }: EChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts | null>(null);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }
    const chart = echarts.init(containerRef.current);
    chartRef.current = chart;
    const resize = () => chart.resize();
    window.addEventListener("resize", resize);
    return () => {
      window.removeEventListener("resize", resize);
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  useEffect(() => {
    chartRef.current?.setOption(option, true);
  }, [option]);

  useEffect(() => {
    const chart = chartRef.current;
    if (!chart || !onClickCategory) {
      return;
    }
    const handler = (params: { name?: string }) => {
      if (typeof params.name === "string") {
        onClickCategory(params.name);
      }
    };
    chart.on("click", handler);
    return () => {
      chart.off("click", handler);
    };
  }, [onClickCategory]);

  return <div data-testid="echart" ref={containerRef} style={{ width: "100%", height }} />;
}
