package com.singleone.backend.upload;

/**
 * PRD 11.5: 오류가 하나라도 존재하면 전체 파일 반영을 취소하고 row-specific error를 제공한다.
 */
public record RowValidationError(long rowNo, String errorCode, String message) {
}
