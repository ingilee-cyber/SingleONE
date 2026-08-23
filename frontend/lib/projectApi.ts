import apiClient from "@/lib/apiClient";

// Backend com.singleone.backend.domain.common.Media, project.ProjectResponse/CampaignOptionResponse와 1:1 대응.
export type Media = "META" | "TIKTOK" | "GOOGLE" | "NAVER" | "CRITEO";

export const MEDIA_LIST: Media[] = ["META", "TIKTOK", "GOOGLE", "NAVER", "CRITEO"];

export interface CampaignOption {
  media: Media;
  campaignId: string;
  campaignName: string;
}

export interface Project {
  projectId: number;
  advertiserId: string;
  projectName: string;
  systemDefault: boolean;
  referenceOnly: boolean;
  campaigns: CampaignOption[];
  createdAt: string | null;
  updatedAt: string | null;
}

export interface CampaignSelection {
  media: Media;
  campaignId: string;
}

export interface ProjectUpsertRequest {
  projectName: string;
  campaigns: CampaignSelection[];
}

export const listProjects = (advertiserId: string, search?: string) =>
  apiClient
    .get<{ content: Project[] }>(`/api/v1/advertisers/${advertiserId}/projects`, {
      params: { search: search || undefined, size: 200 },
    })
    .then((res) => res.data.content);

export const createProject = (advertiserId: string, request: ProjectUpsertRequest) =>
  apiClient.post<Project>(`/api/v1/advertisers/${advertiserId}/projects`, request).then((res) => res.data);

export const updateProject = (projectId: number, request: ProjectUpsertRequest) =>
  apiClient.put<Project>(`/api/v1/projects/${projectId}`, request).then((res) => res.data);

export const deleteProject = (projectId: number) => apiClient.delete(`/api/v1/projects/${projectId}`);

export const searchCampaigns = (advertiserId: string, search?: string, media?: Media) =>
  apiClient
    .get<{ content: CampaignOption[] }>(`/api/v1/advertisers/${advertiserId}/campaigns`, {
      params: { search: search || undefined, media, size: 200 },
    })
    .then((res) => res.data.content);
