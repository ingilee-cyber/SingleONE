package com.singleone.backend.domain.project;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * PRD 12.1 Project, PRD 5.1/5.2 규칙 대상.
 * project_id/project_name 형식은 PRD가 지정하지 않아 자동증가 BIGINT로 구현한다.
 * (advertiser_id, project_name) unique 제약은 DB(V3 마이그레이션)에서 강제한다.
 * 생성/수정/삭제 및 "최소 2개 매체" 등 검증 로직은 프로젝트 CRUD 단계에서 구현한다.
 */
@Entity
@Table(name = "project")
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "advertiser_id", nullable = false, length = 100)
	private String advertiserId;

	@Column(name = "project_name", nullable = false)
	private String projectName;

	@Column(name = "system_default", nullable = false)
	private boolean systemDefault;

	@Column(name = "reference_only", nullable = false)
	private boolean referenceOnly;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	protected Project() {
	}

	public Project(String advertiserId, String projectName, boolean systemDefault, boolean referenceOnly) {
		this.advertiserId = advertiserId;
		this.projectName = projectName;
		this.systemDefault = systemDefault;
		this.referenceOnly = referenceOnly;
	}

	public Long getProjectId() {
		return projectId;
	}

	public String getAdvertiserId() {
		return advertiserId;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public boolean isSystemDefault() {
		return systemDefault;
	}

	public boolean isReferenceOnly() {
		return referenceOnly;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
