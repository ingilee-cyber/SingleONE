import apiClient from "@/lib/apiClient";
import type { Media } from "@/lib/projectApi";

// Backend com.singleone.backend.simulation.*와 1:1 대응.
export interface SimulationRequest {
  baseFrom: string;
  baseTo: string;
  simFrom: string;
  simTo: string;
  mediaBudgets: Partial<Record<Media, number>>;
}

export interface CurvePoint {
  weeklyCost: number;
  predictedPurchases: number;
  predictedRevenue: number;
}

export type ConfidenceLevel = "HIGH" | "MEDIUM" | "LOW" | "UNAVAILABLE";

export interface MediaSimulationResult {
  media: Media;
  inputBudget: number;
  weeklyBudget: number;
  convertedCurrentBudget: number;
  convertedCurrentWeeklyBudget: number;
  confidence: ConfidenceLevel | null;
  predictedPurchases: number | null;
  predictedRevenue: number | null;
  predictedCpa: number | null;
  predictedRoas: number | null;
  historicalMinWeeklyCost: number | null;
  historicalMaxWeeklyCost: number | null;
  curvePoints: CurvePoint[];
  notes: string[];
}

export interface SimulationResult {
  mediaResults: MediaSimulationResult[];
  totalBudget: number;
  totalAvailable: boolean;
  totalPredictedPurchases: number | null;
  totalPredictedRevenue: number | null;
  totalPredictedCpa: number | null;
  totalPredictedRoas: number | null;
  disclaimer: string;
}

export const postSimulation = (projectId: number, request: SimulationRequest) =>
  apiClient.post<SimulationResult>(`/api/v1/projects/${projectId}/simulation`, request).then((res) => res.data);
