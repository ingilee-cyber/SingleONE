package com.singleone.backend.common.time;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * CLAUDE.md Hard Rule: Backend/DB Timestamp 저장 기준은 UTC이고, timezone 정보가 없는
 * 업로드 날짜/시간은 Asia/Seoul로 해석한다. 화면/기능마다 다르게 계산하지 않도록 이 공통
 * Utility 하나만 사용한다.
 */
public final class TimeUtils {

	public static final ZoneId UPLOAD_DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

	private TimeUtils() {
	}

	/**
	 * ISO-8601 오프셋/'Z'가 포함된 값은 그대로 UTC Instant로 변환하고, 오프셋이 없는 값은
	 * Asia/Seoul로 해석해 UTC Instant로 변환한다.
	 */
	public static Instant parseUploadTimestamp(String value) {
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException ignored) {
			// timezone 정보가 없는 형식일 수 있으므로 아래에서 재시도한다.
		}
		try {
			LocalDateTime localDateTime = LocalDateTime.parse(value);
			return localDateTime.atZone(UPLOAD_DEFAULT_ZONE).toInstant();
		} catch (DateTimeException e) {
			throw new DateTimeParseException("지원하지 않는 날짜/시간 형식입니다: " + value, value, 0, e);
		}
	}

	/** Asia/Seoul 기준 해당 날짜의 자정(00:00:00)을 UTC Instant로 변환한다. */
	public static Instant startOfDaySeoul(LocalDate date) {
		return date.atStartOfDay(UPLOAD_DEFAULT_ZONE).toInstant();
	}

	/** Asia/Seoul 기준 해당 날짜의 다음날 자정(exclusive 상한)을 UTC Instant로 변환한다. */
	public static Instant endOfDaySeoul(LocalDate date) {
		return date.plusDays(1).atStartOfDay(UPLOAD_DEFAULT_ZONE).toInstant();
	}

}
