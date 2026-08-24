"use client";

import { useMemo } from "react";
import EChart from "@/app/dashboard/EChart";
import type { TopPath } from "@/lib/journeyApi";
import { buildSankeyOption } from "./buildSankeyOption";

export default function SankeyChart({ topPaths }: { topPaths: TopPath[] }) {
  const option = useMemo(() => buildSankeyOption(topPaths), [topPaths]);
  return <EChart option={option} height={400} />;
}
