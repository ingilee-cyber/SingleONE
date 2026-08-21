package com.singleone.backend.migration.clickhouse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * PRD 13.6: "ClickHouse는 versioned SQL migration"을 사용한다고만 명시되어 있고 특정 도구를
 * 지정하지 않아, MySQL Flyway와 독립적으로 동작하는 경량 자체 러너로 구현한다.
 *
 * classpath:db/clickhouse-migration/V{n}__description.sql 파일을 버전 순서로 실행하고,
 * 적용 이력을 ClickHouse의 schema_version 테이블에 기록한다. 이미 적용된 버전은 다시
 * 실행하지 않는다.
 */
@Component
public class ClickHouseMigrationRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ClickHouseMigrationRunner.class);
	private static final String MIGRATION_LOCATION = "classpath:db/clickhouse-migration/*.sql";
	private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__(.*)\\.sql$");

	private final JdbcTemplate clickHouseJdbcTemplate;

	public ClickHouseMigrationRunner(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate) {
		this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		ensureSchemaVersionTable();
		Set<Integer> appliedVersions = fetchAppliedVersions();

		for (MigrationFile migration : loadMigrationFiles()) {
			if (appliedVersions.contains(migration.version())) {
				continue;
			}
			log.info("Applying ClickHouse migration V{}__{}", migration.version(), migration.description());
			executeStatements(migration.sql());
			recordApplied(migration.version(), migration.description());
		}
	}

	private void ensureSchemaVersionTable() {
		clickHouseJdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS schema_version (
				version UInt32,
				description String,
				applied_at DateTime DEFAULT now()
			) ENGINE = MergeTree ORDER BY version
			""");
	}

	private Set<Integer> fetchAppliedVersions() {
		List<Integer> versions = clickHouseJdbcTemplate.queryForList(
			"SELECT version FROM schema_version", Integer.class);
		return new HashSet<>(versions);
	}

	private List<MigrationFile> loadMigrationFiles() throws IOException {
		ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource[] resources = resolver.getResources(MIGRATION_LOCATION);
		List<MigrationFile> migrations = new ArrayList<>();

		for (Resource resource : resources) {
			String filename = resource.getFilename();
			if (filename == null) {
				continue;
			}
			Matcher matcher = VERSION_PATTERN.matcher(filename);
			if (!matcher.matches()) {
				log.warn("ClickHouse migration 파일명이 V{{n}}__{{description}}.sql 형식이 아니어서 건너뜁니다: {}", filename);
				continue;
			}
			int version = Integer.parseInt(matcher.group(1));
			String description = matcher.group(2);
			String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
			migrations.add(new MigrationFile(version, description, sql));
		}

		migrations.sort(Comparator.comparingInt(MigrationFile::version));
		return migrations;
	}

	private void executeStatements(String sql) {
		for (String statement : sql.split(";")) {
			String cleaned = stripCommentLines(statement).strip();
			if (cleaned.isEmpty()) {
				continue;
			}
			clickHouseJdbcTemplate.execute(cleaned);
		}
	}

	/**
	 * 각 줄 단위로 "--" 주석만 제거한다. 원래 statement.strip()만으로는 SQL 문이 주석 줄로
	 * 시작하기만 해도 전체 블록이 주석으로 오인되어 실행되지 않는 버그가 있었다.
	 */
	String stripCommentLines(String sql) {
		StringBuilder result = new StringBuilder();
		for (String line : sql.split("\n")) {
			if (line.strip().startsWith("--")) {
				continue;
			}
			result.append(line).append('\n');
		}
		return result.toString();
	}

	private void recordApplied(int version, String description) {
		clickHouseJdbcTemplate.update(
			"INSERT INTO schema_version (version, description) VALUES (?, ?)",
			version, description);
	}

	private record MigrationFile(int version, String description, String sql) {
	}

}
