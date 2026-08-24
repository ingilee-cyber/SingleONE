package com.singleone.backend.domain.master;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.singleone.backend.domain.common.Media;

public interface AdMasterRepository extends JpaRepository<AdMaster, AdMasterId> {

	List<AdMaster> findByIdAdvertiserIdAndIdMediaAndIdCampaignIdAndIdAdGroupId(String advertiserId, Media media,
		String campaignId, String adGroupId);

}
