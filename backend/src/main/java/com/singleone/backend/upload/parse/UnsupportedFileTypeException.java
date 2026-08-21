package com.singleone.backend.upload.parse;

public class UnsupportedFileTypeException extends RuntimeException {

	public UnsupportedFileTypeException(String filename) {
		super("지원하지 않는 파일 형식입니다: " + filename);
	}

}
