package com.singleone.backend.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * MySQL이 JPA/Hibernate가 사용하는 유일한 기본(Primary) DataSource임을 명시한다.
 * ClickHouseConfig의 별도 DataSource와 타입 충돌 없이 구분되도록 @Primary로 고정한다.
 *
 * DataSourceProperties를 거치지 않고 DataSourceBuilder에 spring.datasource.*를 직접
 * 바인딩하면 "url"이 HikariDataSource의 setJdbcUrl()로 매핑되지 않아 연결이 실패한다.
 * DataSourceProperties.initializeDataSourceBuilder()를 사용해 이 매핑을 올바르게 처리한다.
 */
@Configuration
public class DataSourceConfig {

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource")
	public DataSourceProperties dataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@Primary
	public DataSource dataSource(DataSourceProperties properties) {
		return properties.initializeDataSourceBuilder().build();
	}

}
