package com.singleone.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ClickHouse 연결 설정. spring.datasource.* 와 분리된 전용 네임스페이스를 사용해
 * Spring Boot의 JPA/Hibernate 자동설정이 ClickHouse를 기본 DataSource로 인식하지 않도록 한다.
 */
@ConfigurationProperties(prefix = "singleone.clickhouse")
public class ClickHouseProperties {

	private String url;
	private String username;
	private String password;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

}
