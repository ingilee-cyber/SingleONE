package com.singleone.backend.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * PRD 11.8: 파일 업로드는 비동기 Job으로 처리한다. PRD 기술 스택에 별도 메시지 큐/잡
 * 러너가 없어, 추가 인프라 없이 Spring 내장 @Async + 전용 스레드풀로 구현한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

	@Override
	@Bean(name = "uploadTaskExecutor")
	public Executor getAsyncExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("upload-async-");
		executor.initialize();
		return executor;
	}

}
