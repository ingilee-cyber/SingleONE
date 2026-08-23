package com.singleone.backend.domain.project;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCampaignRepository extends JpaRepository<ProjectCampaign, ProjectCampaignId> {

	List<ProjectCampaign> findByIdProjectId(Long projectId);

}
