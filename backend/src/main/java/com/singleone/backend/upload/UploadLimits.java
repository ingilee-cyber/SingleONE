package com.singleone.backend.upload;

/**
 * PRD 11.11 제한.
 */
public final class UploadLimits {

	public static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
	public static final long MAX_ROWS = 1_000_000L;
	public static final int INSERT_CHUNK_SIZE = 5_000;

	private UploadLimits() {
	}

}
