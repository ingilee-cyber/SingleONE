package com.singleone.backend.domain.upload;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadBatchRepository extends JpaRepository<UploadBatch, Long> {

	List<UploadBatch> findByAdvertiserIdAndTypeAndStatus(String advertiserId, UploadType type, UploadStatus status);

	Page<UploadBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);

}
