package com.singleone.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI singleOneOpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("SingleONE API")
				.description("SingleONE 테스트 제품 Backend API (PRD 13.3~13.4 기준)")
				.version("v1"));
	}

}
