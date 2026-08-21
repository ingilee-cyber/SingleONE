package com.singleone.backend.domain.upload;

/**
 * PRD 11.7 업로드 상태 머신.
 */
public enum UploadStatus {
	VALIDATING,
	DUPLICATE_CONFIRMATION_REQUIRED,
	IMPORTING,
	SUCCESS,
	FAILED,
	CANCELLED
}
