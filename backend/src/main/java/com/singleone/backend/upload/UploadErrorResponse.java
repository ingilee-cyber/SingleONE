package com.singleone.backend.upload;

import com.singleone.backend.domain.upload.UploadError;

public record UploadErrorResponse(long rowNo, String errorCode, String message) {

	public static UploadErrorResponse from(UploadError error) {
		return new UploadErrorResponse(error.getRowNo(), error.getErrorCode(), error.getMessage());
	}

}
