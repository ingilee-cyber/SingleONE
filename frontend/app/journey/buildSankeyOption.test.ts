import { describe, expect, it } from "vitest";
import { buildSankeyOption } from "./buildSankeyOption";
import type { TopPath } from "@/lib/journeyApi";

function series(option: ReturnType<typeof buildSankeyOption>) {
  // @ts-expect-error - test-only access into the loosely-typed EChartsCoreOption.
  return option.series[0] as { data: { name: string }[]; links: { source: string; target: string; value: number }[] };
}

describe("buildSankeyOption", () => {
  it("builds nodes and links for a simple two-channel path", () => {
    const topPaths: TopPath[] = [{ channels: ["META", "GOOGLE"], purchaseCount: 2, purchaseRevenue: 200000 }];
    const { data, links } = series(buildSankeyOption(topPaths));

    expect(data.map((n) => n.name)).toEqual(["1. META", "2. GOOGLE", "구매"]);
    expect(links).toEqual([
      { source: "1. META", target: "2. GOOGLE", value: 2 },
      { source: "2. GOOGLE", target: "구매", value: 2 },
    ]);
  });

  it("aggregates link values across paths sharing the same prefix", () => {
    const topPaths: TopPath[] = [
      { channels: ["META", "GOOGLE"], purchaseCount: 2, purchaseRevenue: 200000 },
      { channels: ["META", "TIKTOK"], purchaseCount: 1, purchaseRevenue: 50000 },
    ];
    const { links } = series(buildSankeyOption(topPaths));

    const metaToGoogle = links.find((l) => l.source === "1. META" && l.target === "2. GOOGLE");
    const metaToTiktok = links.find((l) => l.source === "1. META" && l.target === "2. TIKTOK");
    expect(metaToGoogle?.value).toBe(2);
    expect(metaToTiktok?.value).toBe(1);
  });

  it("keeps non-consecutive repeated channels as distinct step nodes to avoid a Sankey cycle", () => {
    const topPaths: TopPath[] = [{ channels: ["META", "GOOGLE", "META"], purchaseCount: 1, purchaseRevenue: 30000 }];
    const { data, links } = series(buildSankeyOption(topPaths));

    expect(data.map((n) => n.name)).toEqual(["1. META", "2. GOOGLE", "3. META", "구매"]);
    expect(links).toEqual([
      { source: "1. META", target: "2. GOOGLE", value: 1 },
      { source: "2. GOOGLE", target: "3. META", value: 1 },
      { source: "3. META", target: "구매", value: 1 },
    ]);
  });
});
