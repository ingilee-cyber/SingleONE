package com.singleone.backend.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

class TimeUtilsTest {

	@Test
	void parsesUtcOffsetDirectly() {
		Instant result = TimeUtils.parseUploadTimestamp("2026-08-12T00:00:00Z");
		assertThat(result).isEqualTo(Instant.parse("2026-08-12T00:00:00Z"));
	}

	@Test
	void interpretsNoOffsetValueAsAsiaSeoul() {
		// CLAUDE.md Hard Rule: timezone 정보가 없는 값은 Asia/Seoul(UTC+9)로 해석한다.
		Instant result = TimeUtils.parseUploadTimestamp("2026-08-12T09:00:00");
		assertThat(result).isEqualTo(Instant.parse("2026-08-12T00:00:00Z"));
	}

	@Test
	void rejectsUnparsableValue() {
		assertThatThrownBy(() -> TimeUtils.parseUploadTimestamp("not-a-date"))
			.isInstanceOf(DateTimeParseException.class);
	}

}
