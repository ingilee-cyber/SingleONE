package com.singleone.backend.domain.master;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * PRD 12.1 Ad Master.
 */
@Entity
@Table(name = "ad_master")
public class AdMaster {

	@EmbeddedId
	private AdMasterId id;

	@Column(name = "latest_name", nullable = false)
	private String latestName;

	@Column(name = "latest_source_date")
	private LocalDate latestSourceDate;

	@Column(name = "latest_upload_batch_id")
	private Long latestUploadBatchId;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	protected AdMaster() {
	}

	public AdMaster(AdMasterId id, String latestName) {
		this.id = id;
		this.latestName = latestName;
	}

	public AdMasterId getId() {
		return id;
	}

	public String getLatestName() {
		return latestName;
	}

	public void setLatestName(String latestName) {
		this.latestName = latestName;
	}

	public LocalDate getLatestSourceDate() {
		return latestSourceDate;
	}

	public void setLatestSourceDate(LocalDate latestSourceDate) {
		this.latestSourceDate = latestSourceDate;
	}

	public Long getLatestUploadBatchId() {
		return latestUploadBatchId;
	}

	public void setLatestUploadBatchId(Long latestUploadBatchId) {
		this.latestUploadBatchId = latestUploadBatchId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
