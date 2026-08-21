package com.singleone.backend.upload.parse;

import java.util.Map;

/**
 * 헤더가 컬럼명으로 매핑된 원본 데이터 행. rowNo는 헤더를 제외한 1부터 시작하는 데이터 행 번호다
 * (UploadError.row_no에 그대로 사용).
 */
public record RawRow(long rowNo, Map<String, String> values) {

	public String get(String column) {
		String value = values.get(column);
		return value == null ? null : value.strip();
	}

}
