import type { EChartsCoreOption } from "echarts";
import type { TopPath } from "@/lib/journeyApi";
import { mediaColor } from "@/lib/mediaColors";

const PURCHASE_NODE = "구매";
const PURCHASE_NODE_COLOR = "#16181D";

function stepNode(channel: string, index: number) {
  // 같은 채널이 같은 경로 안에서 비연속으로 반복될 수 있어(PRD AC-36 Meta→Google→Meta 사례)
  // 채널명만 노드로 쓰면 Sankey가 순환 그래프가 되어 렌더링에 실패한다. 단계 번호를 붙여
  // "1. Meta"/"3. Meta"처럼 서로 다른 노드로 취급해 순환을 없앤다.
  return `${index + 1}. ${channel}`;
}

/** "1. META" 형태의 노드명에서 채널명만 분리해 매체 고정 색을 찾는다(구매 노드는 중립색). */
function nodeColor(nodeName: string): string {
  if (nodeName === PURCHASE_NODE) {
    return PURCHASE_NODE_COLOR;
  }
  const channel = nodeName.replace(/^\d+\.\s*/, "");
  return mediaColor(channel);
}

/** topPaths(Top 20)에서 Sankey 노드/링크를 만드는 순수 함수. */
export function buildSankeyOption(topPaths: TopPath[]): EChartsCoreOption {
  const nodeNames = new Set<string>();
  const linkValues = new Map<string, number>();

  for (const path of topPaths) {
    const nodes = [...path.channels.map((channel, index) => stepNode(channel, index)), PURCHASE_NODE];
    nodes.forEach((name) => nodeNames.add(name));
    for (let i = 0; i < nodes.length - 1; i++) {
      const key = JSON.stringify([nodes[i], nodes[i + 1]]);
      linkValues.set(key, (linkValues.get(key) ?? 0) + path.purchaseCount);
    }
  }

  const links = Array.from(linkValues.entries()).map(([key, value]) => {
    const [source, target] = JSON.parse(key) as [string, string];
    return { source, target, value };
  });

  return {
    series: [
      {
        type: "sankey",
        data: Array.from(nodeNames, (name) => ({ name, itemStyle: { color: nodeColor(name) } })),
        links,
        emphasis: { focus: "adjacency" },
        lineStyle: { color: "gradient", curveness: 0.5 },
      },
    ],
  } as EChartsCoreOption;
}
