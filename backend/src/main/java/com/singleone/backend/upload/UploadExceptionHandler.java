package com.singleone.backend.upload;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class UploadExceptionHandler {

	public record ErrorBody(String message) {
	}

	@ExceptionHandler(UploadRequestException.class)
	public ResponseEntity<ErrorBody> handleUploadRequestException(UploadRequestException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(e.getMessage()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorBody> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody("파일 크기는 최대 50MB까지 허용됩니다."));
	}

}
