import type { Media } from "./projectApi";

/**
 * 매체별 고정 색상 매핑. Index 차트/Rolling 차트/Sankey/Simulation 곡선 등 매체가 등장하는
 * 모든 시각화가 이 값을 공유해 같은 매체는 항상 같은 색으로 보이게 한다(디자인 개선 범위,
 * 매체 목록 자체는 lib/projectApi.ts의 MEDIA_LIST를 그대로 따른다).
 */
export const MEDIA_COLORS: Record<Media, string> = {
  META: "#3B63E0",
  GOOGLE: "#2E9E83",
  TIKTOK: "#1F2937",
  NAVER: "#C99A3D",
  CRITEO: "#7C6FE0",
};

export const NEUTRAL_SERIES_COLOR = "#94A3B8";

export function mediaColor(media: string): string {
  return (MEDIA_COLORS as Record<string, string>)[media] ?? NEUTRAL_SERIES_COLOR;
}
