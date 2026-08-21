package com.singleone.backend.upload;

import java.time.Instant;

import com.singleone.backend.domain.upload.UploadBatch;
import com.singleone.backend.domain.upload.UploadStatus;
import com.singleone.backend.domain.upload.UploadType;

/**
 * PRD 11.9 업로드 이력에 필요한 필드(일시, 종류, 파일명, 광고주, 반영 건수, 상태)를 담는다.
 */
public record UploadBatchResponse(
	Long uploadBatchId,
	String advertiserId,
	UploadType type,
	String filename,
	UploadStatus status,
	Long totalRows,
	Long successRows,
	Long errorRows,
	Instant createdAt,
	Instant updatedAt
) {

	public static UploadBatchResponse from(UploadBatch batch) {
		return new UploadBatchResponse(
			batch.getUploadBatchId(), batch.getAdvertiserId(), batch.getType(), batch.getFilename(),
			batch.getStatus(), batch.getTotalRows(), batch.getSuccessRows(), batch.getErrorRows(),
			batch.getCreatedAt(), batch.getUpdatedAt());
	}

}
