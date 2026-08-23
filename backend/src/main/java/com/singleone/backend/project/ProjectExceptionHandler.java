package com.singleone.backend.project;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProjectExceptionHandler {

	public record ErrorBody(String message) {
	}

	@ExceptionHandler(ProjectRequestException.class)
	public ResponseEntity<ErrorBody> handleProjectRequestException(ProjectRequestException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(e.getMessage()));
	}

}
