package com.singleone.backend.simulation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * PRD 10.4 매체별 한계 효율 모델: y = a·ln(x) + b, x=주간 cost, OLS로 적합한다.
 * Spring/DB에 의존하지 않는 순수 계산이라 Docker 없이도 단위 테스트를 항상 실행할 수 있다
 * ({@code com.singleone.backend.analytics.SingleOneIndexCalculator}와 동일한 이유).
 *
 * 자연로그·최소제곱은 BigDecimal에 없는 초월함수 연산이 필요해 이 클래스 내부에서만 {@code double}을
 * 사용한다(사용자 확인 사항 — 이 계산 전후의 주간 SingleONE 구매/매출 집계, 기간 환산, CPA/ROAS는
 * 기존과 동일하게 BigDecimal을 유지한다).
 */
@Component
public class WeeklyLogModelFitter {

	private static final double MIN_R_SQUARED = 0.50;

	/** PRD 10.5: 증가형+한계 효율 감소형(a&gt;0) 및 R²&gt;=0.50을 함께 판정한다. */
	public LogModel fit(List<BigDecimal> weeklyCosts, List<BigDecimal> weeklyValues) {
		int n = weeklyCosts.size();
		if (n < 2 || n != weeklyValues.size()) {
			return LogModel.INVALID;
		}

		double[] u = new double[n];
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			u[i] = Math.log(weeklyCosts.get(i).doubleValue());
			y[i] = weeklyValues.get(i).doubleValue();
		}

		double sumU = 0;
		double sumY = 0;
		double sumUY = 0;
		double sumUU = 0;
		for (int i = 0; i < n; i++) {
			sumU += u[i];
			sumY += y[i];
			sumUY += u[i] * y[i];
			sumUU += u[i] * u[i];
		}

		double denominator = n * sumUU - sumU * sumU;
		if (denominator == 0) {
			return LogModel.INVALID;
		}
		double a = (n * sumUY - sumU * sumY) / denominator;
		double b = (sumY - a * sumU) / n;

		double meanY = sumY / n;
		double ssRes = 0;
		double ssTot = 0;
		for (int i = 0; i < n; i++) {
			double predicted = a * u[i] + b;
			ssRes += Math.pow(y[i] - predicted, 2);
			ssTot += Math.pow(y[i] - meanY, 2);
		}
		double rSquared = ssTot == 0 ? (ssRes == 0 ? 1.0 : 0.0) : 1.0 - ssRes / ssTot;

		boolean valid = a > 0 && rSquared >= MIN_R_SQUARED;
		return new LogModel(
			BigDecimal.valueOf(a).setScale(10, RoundingMode.HALF_UP),
			BigDecimal.valueOf(b).setScale(10, RoundingMode.HALF_UP),
			BigDecimal.valueOf(rSquared).setScale(10, RoundingMode.HALF_UP),
			valid);
	}

	/** PRD 10.6/8.4 스타일로 회귀식을 적용해 예상값을 계산한다. 음수는 0으로 보정한다. */
	public BigDecimal predict(LogModel model, BigDecimal x) {
		double predicted = model.a().doubleValue() * Math.log(x.doubleValue()) + model.b().doubleValue();
		if (predicted < 0) {
			predicted = 0;
		}
		return BigDecimal.valueOf(predicted);
	}

}
