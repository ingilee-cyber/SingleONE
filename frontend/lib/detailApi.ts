import apiClient from "@/lib/apiClient";
import type { Media } from "@/lib/projectApi";
import type {
  MediaIndexResult,
  MediaPerformanceTotals,
  OriginalPerformance,
  RollingIndexPoint,
  SingleOnePerformance,
} from "@/lib/dashboardApi";

// Backend com.singleone.backend.detail.*와 1:1 대응.
export interface EntityPerformance {
  id: string;
  name: string;
  rawTotals: MediaPerformanceTotals;
  rawPerformance: OriginalPerformance;
  singleOnePerformance: SingleOnePerformance;
}

export interface EntityPerformanceComparison {
  current: EntityPerformance;
  previous: EntityPerformance;
}

export interface MediaDetailResponse {
  current: MediaIndexResult;
  previous: MediaIndexResult;
  rolling: RollingIndexPoint[];
}

export interface EntityPage {
  content: EntityPerformance[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface ListParams {
  from: string;
  to: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const getMediaDetail = (projectId: number, media: Media, from: string, to: string) =>
  apiClient
    .get<MediaDetailResponse>(`/api/v1/projects/${projectId}/media/${media}/summary`, { params: { from, to } })
    .then((res) => res.data);

export const listCampaigns = (projectId: number, media: Media, params: ListParams) =>
  apiClient
    .get<EntityPage>(`/api/v1/projects/${projectId}/media/${media}/campaigns`, { params })
    .then((res) => res.data);

export const getCampaignDetail = (projectId: number, media: Media, campaignId: string, from: string, to: string) =>
  apiClient
    .get<EntityPerformanceComparison>(`/api/v1/projects/${projectId}/media/${media}/campaigns/${campaignId}/summary`, {
      params: { from, to },
    })
    .then((res) => res.data);

export const listAdGroups = (projectId: number, media: Media, campaignId: string, params: ListParams) =>
  apiClient
    .get<EntityPage>(`/api/v1/projects/${projectId}/media/${media}/campaigns/${campaignId}/ad-groups`, { params })
    .then((res) => res.data);

export const getAdGroupDetail = (
  projectId: number,
  media: Media,
  campaignId: string,
  adGroupId: string,
  from: string,
  to: string,
) =>
  apiClient
    .get<EntityPerformance>(
      `/api/v1/projects/${projectId}/media/${media}/campaigns/${campaignId}/ad-groups/${adGroupId}/summary`,
      { params: { from, to } },
    )
    .then((res) => res.data);

export const listAds = (projectId: number, media: Media, campaignId: string, adGroupId: string, params: ListParams) =>
  apiClient
    .get<EntityPage>(`/api/v1/projects/${projectId}/media/${media}/campaigns/${campaignId}/ad-groups/${adGroupId}/ads`, {
      params,
    })
    .then((res) => res.data);

export const getAdDetail = (
  projectId: number,
  media: Media,
  campaignId: string,
  adGroupId: string,
  adId: string,
  from: string,
  to: string,
) =>
  apiClient
    .get<EntityPerformance>(
      `/api/v1/projects/${projectId}/media/${media}/campaigns/${campaignId}/ad-groups/${adGroupId}/ads/${adId}/summary`,
      { params: { from, to } },
    )
    .then((res) => res.data);
