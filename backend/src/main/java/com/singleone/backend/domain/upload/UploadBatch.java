package com.singleone.backend.domain.upload;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * PRD 12.1 UploadBatch, PRD 11.7 상태 머신, PRD 11.9 업로드 이력.
 * 실제 상태 전이/검증/집계 로직은 업로드 처리 단계에서 구현한다.
 */
@Entity
@Table(name = "upload_batch")
public class UploadBatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "upload_batch_id")
	private Long uploadBatchId;

	@Column(name = "advertiser_id", nullable = false, length = 100)
	private String advertiserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 20)
	private UploadType type;

	@Column(name = "filename", nullable = false)
	private String filename;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 40)
	private UploadStatus status;

	@Column(name = "total_rows")
	private Long totalRows;

	@Column(name = "success_rows")
	private Long successRows;

	@Column(name = "error_rows")
	private Long errorRows;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	protected UploadBatch() {
	}

	public UploadBatch(String advertiserId, UploadType type, String filename, UploadStatus status) {
		this.advertiserId = advertiserId;
		this.type = type;
		this.filename = filename;
		this.status = status;
	}

	public Long getUploadBatchId() {
		return uploadBatchId;
	}

	public String getAdvertiserId() {
		return advertiserId;
	}

	public UploadType getType() {
		return type;
	}

	public String getFilename() {
		return filename;
	}

	public UploadStatus getStatus() {
		return status;
	}

	public void setStatus(UploadStatus status) {
		this.status = status;
	}

	public Long getTotalRows() {
		return totalRows;
	}

	public void setTotalRows(Long totalRows) {
		this.totalRows = totalRows;
	}

	public Long getSuccessRows() {
		return successRows;
	}

	public void setSuccessRows(Long successRows) {
		this.successRows = successRows;
	}

	public Long getErrorRows() {
		return errorRows;
	}

	public void setErrorRows(Long errorRows) {
		this.errorRows = errorRows;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
