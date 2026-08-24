"use client";

import { useMemo } from "react";
import EChart from "@/app/dashboard/EChart";
import type { MediaSimulationResult } from "@/lib/simulationApi";
import { buildMarginalEfficiencyOption } from "./buildMarginalEfficiencyOption";

export default function MarginalEfficiencyChart({ result }: { result: MediaSimulationResult }) {
  const option = useMemo(() => buildMarginalEfficiencyOption(result), [result]);
  if (result.curvePoints.length === 0) {
    return null;
  }
  return <EChart option={option} height={280} />;
}
