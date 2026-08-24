import apiClient from "@/lib/apiClient";
import type { Media } from "@/lib/projectApi";

// Backend com.singleone.backend.journey.*와 1:1 대응.
export interface TopPath {
  channels: Media[];
  purchaseCount: number;
  purchaseRevenue: number;
}

export interface ChannelAttributionRow {
  channel: Media;
  attributedPurchases: number;
  attributedRevenue: number;
  sharePercent: number;
}

export interface ChannelPairRow {
  channelA: Media;
  channelB: Media;
  journeyCount: number;
  purchaseRevenue: number;
  sharePercent: number;
}

export interface JourneyAnalysisResult {
  topPaths: TopPath[];
  attribution: ChannelAttributionRow[];
  channelPairs: ChannelPairRow[];
  totalPurchaseJourneys: number;
  attributedJourneyCount: number;
  totalPurchaseRevenue: number;
}

export const getJourneyAnalysis = (projectId: number, from: string, to: string) =>
  apiClient
    .get<JourneyAnalysisResult>(`/api/v1/projects/${projectId}/journey`, { params: { from, to } })
    .then((res) => res.data);
