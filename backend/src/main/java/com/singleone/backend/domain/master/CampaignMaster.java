package com.singleone.backend.domain.master;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * PRD 12.1 Campaign Master: advertiser_id, media, campaign_id, latest name.
 * 표시 이름 갱신 규칙(PRD 5.3)에 필요한 latestSourceDate/latestUploadBatchId는
 * 컬럼만 준비하고, 실제로 채우는 upsert 로직은 업로드 처리 단계에서 구현한다.
 */
@Entity
@Table(name = "campaign_master")
public class CampaignMaster {

	@EmbeddedId
	private CampaignMasterId id;

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

	protected CampaignMaster() {
	}

	public CampaignMaster(CampaignMasterId id, String latestName) {
		this.id = id;
		this.latestName = latestName;
	}

	public CampaignMasterId getId() {
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
