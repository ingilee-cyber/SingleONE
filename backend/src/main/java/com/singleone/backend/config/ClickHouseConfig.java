package com.singleone.backend.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * ClickHouse는 Spring Data JPA/Hibernate 대상이 아니다 (CLAUDE.md Hard Rule 11).
 * MySQL용 기본 DataSource(@Primary, JPA)와는 별도로 이 DataSource/JdbcTemplate만 사용해 접근한다.
 */
@Configuration
@EnableConfigurationProperties(ClickHouseProperties.class)
public class ClickHouseConfig {

	@Bean
	public DataSource clickHouseDataSource(ClickHouseProperties properties) {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
		dataSource.setUrl(properties.getUrl());
		dataSource.setUsername(properties.getUsername());
		dataSource.setPassword(properties.getPassword());
		return dataSource;
	}

	@Bean
	public JdbcTemplate clickHouseJdbcTemplate(@Qualifier("clickHouseDataSource") DataSource clickHouseDataSource) {
		return new JdbcTemplate(clickHouseDataSource);
	}

}
