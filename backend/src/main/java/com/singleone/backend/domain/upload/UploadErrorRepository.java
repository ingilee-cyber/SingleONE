package com.singleone.backend.domain.upload;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadErrorRepository extends JpaRepository<UploadError, Long> {

	List<UploadError> findByUploadBatchIdOrderByRowNoAsc(Long uploadBatchId);

}
