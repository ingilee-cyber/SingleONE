package com.singleone.backend.domain.advertiser;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * PRD 12.1: advertiser_id, latest advertiser_name.
 * 표시 이름 갱신 규칙(PRD 5.3: 최신 date, 동일 date면 최신 SUCCESS batch)에 필요한
 * latestSourceDate/latestUploadBatchId를 Campaign/AdGroup/Ad Master와 동일하게 둔다.
 */
@Entity
@Table(name = "advertiser")
public class Advertiser {

	@Id
	@Column(name = "advertiser_id", length = 100)
	private String advertiserId;

	@Column(name = "advertiser_name", nullable = false)
	private String advertiserName;

	@Column(name = "latest_source_date")
	private LocalDate latestSourceDate;

	@Column(name = "latest_upload_batch_id")
	private Long latestUploadBatchId;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	protected Advertiser() {
	}

	public Advertiser(String advertiserId, String advertiserName) {
		this.advertiserId = advertiserId;
		this.advertiserName = advertiserName;
	}

	public String getAdvertiserId() {
		return advertiserId;
	}

	public String getAdvertiserName() {
		return advertiserName;
	}

	public void setAdvertiserName(String advertiserName) {
		this.advertiserName = advertiserName;
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
