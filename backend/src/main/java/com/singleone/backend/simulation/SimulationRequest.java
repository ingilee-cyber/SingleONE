package com.singleone.backend.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.singleone.backend.domain.common.Media;

/** PRD 10.2 입력. 총예산은 mediaBudgets 합계로 자동 계산되므로 별도 필드가 없다(AC-44). */
public record SimulationRequest(
	LocalDate baseFrom,
	LocalDate baseTo,
	LocalDate simFrom,
	LocalDate simTo,
	Map<Media, BigDecimal> mediaBudgets
) {
}
