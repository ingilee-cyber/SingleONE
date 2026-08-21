package com.singleone.backend.migration.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 마이그레이션 파일(V2__performance_fact.sql 등)이 주석으로 시작하는 CREATE TABLE
 * 문이었는데, statement 전체가 "--"로 시작한다는 이유만으로 통째로 주석 처리되어 실행되지
 * 않은 채 적용 완료로 잘못 기록되는 버그가 있었다 (Docker 미실행 환경에서는 자동 통합
 * 테스트가 막혀 있어 이 순수 단위 테스트로 회귀를 방지한다).
 */
class ClickHouseMigrationRunnerTest {

	private final ClickHouseMigrationRunner runner = new ClickHouseMigrationRunner(mock(JdbcTemplate.class));

	@Test
	void keepsSqlBodyEvenWhenStatementStartsWithACommentLine() {
		String sql = "-- some comment\nCREATE TABLE t (x Int32) ENGINE = Memory";

		String cleaned = runner.stripCommentLines(sql).strip();

		assertThat(cleaned).isEqualTo("CREATE TABLE t (x Int32) ENGINE = Memory");
	}

	@Test
	void pureCommentBlockBecomesEmpty() {
		String sql = "-- only a comment\n-- another comment line";

		String cleaned = runner.stripCommentLines(sql).strip();

		assertThat(cleaned).isEmpty();
	}

}
