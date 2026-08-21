package com.singleone.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.ClickHouseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 애플리케이션 컨텍스트가 MySQL/ClickHouse Bean을 모두 구성하므로, 통합 테스트는 항상 두
 * Testcontainers를 함께 띄워야 한다 (둘 중 하나만으로는 컨텍스트가 뜨지 않음).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

	@Container
	static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
		.withDatabaseName("singleone")
		.withUsername("singleone")
		.withPassword("singleone");

	@Container
	static ClickHouseContainer clickhouse =
		new ClickHouseContainer(DockerImageName.parse("clickhouse/clickhouse-server:24.8"));

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);

		registry.add("singleone.clickhouse.url", clickhouse::getJdbcUrl);
		registry.add("singleone.clickhouse.username", clickhouse::getUsername);
		registry.add("singleone.clickhouse.password", clickhouse::getPassword);
	}

}
