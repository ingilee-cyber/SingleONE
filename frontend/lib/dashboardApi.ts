import apiClient from "@/lib/apiClient";
import type { Media } from "@/lib/projectApi";

// Backend com.singleone.backend.analytics.*, dashboard.DashboardResponse와 1:1 대응.
export type IndexStatus = "VALID" | "INSUFFICIENT_DATA" | "MISSING_REQUIRED_DATA" | "COMPARISON_MEDIA_INSUFFICIENT";

export interface MediaPerformanceTotals {
  media: Media;
  impressions: number;
  clicks: number;
  cost: number;
  rawPurchases: number;
  rawRevenue: number;
  operatingDays: number;
}

export interface OriginalPerformance {
  cpa: number | null;
  roas: number | null;
}

export interface SingleOnePerformance {
  media: Media;
  singleOnePurchases: number;
  singleOneRevenue: number;
  cpa: number | null;
  roas: number | null;
}

export interface IndexComponents {
  exposureIndex: number;
  clickIndex: number;
  purchaseIndex: number;
  revenueIndex: number;
}

export interface MediaIndexResult {
  media: Media;
  status: IndexStatus;
  rawTotals: MediaPerformanceTotals | null;
  rawPerformance: OriginalPerformance | null;
  singleOnePerformance: SingleOnePerformance | null;
  components: IndexComponents | null;
  indexScore: number | null;
}

export interface ProjectTotals {
  impressions: number;
  clicks: number;
  cost: number;
  rawPurchases: number;
  rawRevenue: number;
  rawRoas: number | null;
  singleOnePurchases: number;
  singleOneRevenue: number;
  singleOneRoas: number | null;
}

export interface RollingIndexPoint {
  date: string;
  mediaResults: MediaIndexResult[];
}

export interface DashboardResponse {
  current: MediaIndexResult[];
  currentTotals: ProjectTotals;
  previous: MediaIndexResult[];
  previousTotals: ProjectTotals;
  rolling: RollingIndexPoint[];
}

export const INDEX_STATUS_LABEL: Record<IndexStatus, string> = {
  VALID: "정상",
  INSUFFICIENT_DATA: "데이터 부족",
  MISSING_REQUIRED_DATA: "필수 데이터 누락",
  COMPARISON_MEDIA_INSUFFICIENT: "비교 가능한 매체 부족",
};

export const getDashboard = (projectId: number, from: string, to: string) =>
  apiClient
    .get<DashboardResponse>(`/api/v1/projects/${projectId}/dashboard`, { params: { from, to } })
    .then((res) => res.data);
