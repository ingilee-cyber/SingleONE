import apiClient from "@/lib/apiClient";

// Backend com.singleone.backend.advertiser.AdvertiserResponse와 1:1 대응.
export interface AdvertiserOption {
  advertiserId: string;
  advertiserName: string;
}

export const listAdvertisers = () =>
  apiClient.get<AdvertiserOption[]>("/api/v1/advertisers").then((res) => res.data);
