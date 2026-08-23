package com.singleone.backend.domain.master;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.singleone.backend.domain.common.Media;

public interface CampaignMasterRepository extends JpaRepository<CampaignMaster, CampaignMasterId> {

	List<CampaignMaster> findByIdAdvertiserId(String advertiserId);

	/** PRD 5.3: 캠페인명/ID 검색 및 매체 필터. */
	@Query("SELECT c FROM CampaignMaster c WHERE c.id.advertiserId = :advertiserId "
		+ "AND (:media IS NULL OR c.id.media = :media) "
		+ "AND (:search IS NULL OR LOWER(c.latestName) LIKE LOWER(CONCAT('%', :search, '%')) "
		+ "OR LOWER(c.id.campaignId) LIKE LOWER(CONCAT('%', :search, '%')))")
	Page<CampaignMaster> search(@Param("advertiserId") String advertiserId, @Param("media") Media media,
		@Param("search") String search, Pageable pageable);

}
