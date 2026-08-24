package com.singleone.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.singleone.backend.dashboard.DashboardResponse;
import com.singleone.backend.detail.EntityPerformance;
import com.singleone.backend.detail.EntityPerformanceComparison;
import com.singleone.backend.detail.MediaDetailResponse;

/**
 * AC-25/PRD 8.3: 내부 SingleONE 필터율은 어떤 API 응답 DTO에도 필드로 노출되지 않아야 한다.
 * Spring/DB 없이 record 컴포넌트 이름만 확인하는 순수 구조 검증이라 Docker와 무관하게 항상 실행된다.
 */
class ApiResponseFilterRateNonDisclosureTest {

	private static final List<Class<?>> RESPONSE_DTOS = List.of(
		DashboardResponse.class,
		MediaIndexResult.class,
		SingleOnePerformance.class,
		OriginalPerformance.class,
		MediaPerformanceTotals.class,
		ProjectTotals.class,
		RollingIndexPoint.class,
		IndexComponents.class,
		EntityPerformance.class,
		EntityPerformanceComparison.class,
		MediaDetailResponse.class);

	@Test
	void noResponseDtoExposesAFilterRateField() {
		for (Class<?> dto : RESPONSE_DTOS) {
			assertThat(dto.isRecord()).as(dto.getName() + "는 record여야 한다").isTrue();
			for (RecordComponent component : dto.getRecordComponents()) {
				String name = component.getName().toLowerCase();
				assertThat(name)
					.as(dto.getSimpleName() + "." + component.getName() + " 필드가 내부 필터율을 노출함")
					.doesNotContain("filterrate")
					.doesNotContain("filter_rate");
			}
		}
	}

}
