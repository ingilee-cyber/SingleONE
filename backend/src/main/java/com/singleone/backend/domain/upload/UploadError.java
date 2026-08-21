package com.singleone.backend.domain.upload;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * PRD 12.1 UploadError: upload_batch_id, row_no, error_code, message.
 */
@Entity
@Table(name = "upload_error")
public class UploadError {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "upload_batch_id", nullable = false)
	private Long uploadBatchId;

	@Column(name = "row_no", nullable = false)
	private long rowNo;

	@Column(name = "error_code", nullable = false)
	private String errorCode;

	@Column(name = "message", nullable = false, length = 1000)
	private String message;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected UploadError() {
	}

	public UploadError(Long uploadBatchId, long rowNo, String errorCode, String message) {
		this.uploadBatchId = uploadBatchId;
		this.rowNo = rowNo;
		this.errorCode = errorCode;
		this.message = message;
	}

	public Long getId() {
		return id;
	}

	public Long getUploadBatchId() {
		return uploadBatchId;
	}

	public long getRowNo() {
		return rowNo;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getMessage() {
		return message;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
