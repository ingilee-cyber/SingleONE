package com.singleone.backend.domain.master;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.singleone.backend.domain.common.Media;

public interface AdGroupMasterRepository extends JpaRepository<AdGroupMaster, AdGroupMasterId> {

	List<AdGroupMaster> findByIdAdvertiserIdAndIdMediaAndIdCampaignId(String advertiserId, Media media, String campaignId);

}
