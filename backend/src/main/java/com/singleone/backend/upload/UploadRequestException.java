package com.singleone.backend.upload;

/**
 * 잘못된 업로드 요청(파일 형식/크기, 상태 전이 불가 등)을 나타내는 사용자 오류. Controller에서 400으로 매핑한다.
 */
public class UploadRequestException extends RuntimeException {

	public UploadRequestException(String message) {
		super(message);
	}

}
