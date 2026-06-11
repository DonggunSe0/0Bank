package com.team10.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {

    /**
     * Tesseract OCR 전용 스레드 풀.
     * - corePoolSize(2): 기본 2개 스레드 상시 대기 (OCR은 CPU 집중 작업)
     * - maxPoolSize(4): 동시 요청 폭증 시 최대 4개까지 확장
     * - queueCapacity(50): 대기 큐 초과 시 RejectedExecutionException 발생 → 호출부에서 처리
     * - keepAliveSeconds(60): 유휴 스레드 60초 후 회수
     */
    @Bean(name = "ocrExecutor")
    public Executor ocrExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ocr-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
