package com.singleone.backend.domain.project;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	Optional<Project> findByAdvertiserIdAndSystemDefaultTrue(String advertiserId);

	boolean existsByAdvertiserIdAndProjectName(String advertiserId, String projectName);

	@Query("SELECT p FROM Project p WHERE p.advertiserId = :advertiserId "
		+ "AND (:search IS NULL OR LOWER(p.projectName) LIKE LOWER(CONCAT('%', :search, '%')))")
	Page<Project> search(@Param("advertiserId") String advertiserId, @Param("search") String search, Pageable pageable);

}
