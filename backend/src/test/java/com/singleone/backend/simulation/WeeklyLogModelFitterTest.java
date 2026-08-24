package com.singleone.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * PRD 10.4/10.5 y=a·ln(x)+b OLS 적합 검증. Spring/DB에 의존하지 않는 순수 계산이라 Docker
 * (Testcontainers) 환경과 무관하게 항상 실행된다.
 */
class WeeklyLogModelFitterTest {

	private final WeeklyLogModelFitter fitter = new WeeklyLogModelFitter();

	private static List<BigDecimal> decimals(double... values) {
		List<BigDecimal> result = new java.util.ArrayList<>();
		for (double v : values) {
			result.add(BigDecimal.valueOf(v));
		}
		return result;
	}

	@Test
	void recoversExactCoefficientsFromDataGeneratedByTheSameModel() {
		// y = 50*ln(x) - 100으로 정확히 생성한 데이터라 OLS가 a=50, b=-100, R²=1을 그대로 복원해야 한다.
		double a = 50;
		double b = -100;
		double[] xs = {100, 200, 300, 400, 500, 600};
		List<BigDecimal> costs = new java.util.ArrayList<>();
		List<BigDecimal> values = new java.util.ArrayList<>();
		for (double x : xs) {
			costs.add(BigDecimal.valueOf(x));
			values.add(BigDecimal.valueOf(a * Math.log(x) + b));
		}

		LogModel model = fitter.fit(costs, values);

		assertThat(model.a().setScale(4, RoundingMode.HALF_UP)).isEqualByComparingTo("50.0000");
		assertThat(model.b().setScale(4, RoundingMode.HALF_UP)).isEqualByComparingTo("-100.0000");
		assertThat(model.rSquared().setScale(4, RoundingMode.HALF_UP)).isEqualByComparingTo("1.0000");
		assertThat(model.valid()).isTrue();
	}

	@Test
	void ac47_decreasingCurveIsInvalidEvenWithPerfectFit() {
		// a<=0(감소형)이면 R²가 완벽해도 무효(PRD 10.5 "곡선 형태" 조건).
		double a = -10;
		double b = 200;
		double[] xs = {100, 200, 300, 400, 500, 600};
		List<BigDecimal> costs = new java.util.ArrayList<>();
		List<BigDecimal> values = new java.util.ArrayList<>();
		for (double x : xs) {
			costs.add(BigDecimal.valueOf(x));
			values.add(BigDecimal.valueOf(a * Math.log(x) + b));
		}

		LogModel model = fitter.fit(costs, values);

		assertThat(model.rSquared().setScale(4, RoundingMode.HALF_UP)).isEqualByComparingTo("1.0000");
		assertThat(model.a().signum()).isLessThanOrEqualTo(0);
		assertThat(model.valid()).isFalse();
	}

	@Test
	void ac47_lowRSquaredIsInvalidEvenWithPositiveSlope() {
		// 노이즈가 큰 데이터라 R²가 낮아야 한다(a>0이라도 적합도 미달이면 무효).
		List<BigDecimal> costs = decimals(100, 200, 300, 400, 500, 600);
		List<BigDecimal> values = decimals(10, 500, 5, 480, 15, 470);

		LogModel model = fitter.fit(costs, values);

		assertThat(model.rSquared()).isLessThan(new BigDecimal("0.50"));
		assertThat(model.valid()).isFalse();
	}

	@Test
	void tooFewPointsIsInvalid() {
		LogModel model = fitter.fit(decimals(100), decimals(50));
		assertThat(model).isEqualTo(LogModel.INVALID);
	}

	@Test
	void predictFloorsNegativeValuesToZero() {
		LogModel model = new LogModel(new BigDecimal("1"), new BigDecimal("-1000"), new BigDecimal("0.9"), true);
		BigDecimal predicted = fitter.predict(model, new BigDecimal("10"));
		assertThat(predicted).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void predictAppliesTheFittedFormula() {
		LogModel model = new LogModel(new BigDecimal("50"), new BigDecimal("-100"), new BigDecimal("1"), true);
		BigDecimal predicted = fitter.predict(model, new BigDecimal("300"));
		// 50*ln(300)-100 = 185.1888...
		assertThat(predicted.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("185.19");
	}

}
