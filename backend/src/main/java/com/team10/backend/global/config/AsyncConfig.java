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
     * 신분증 OCR(CODEF) → 행안부 검증 체이닝 전용 스레드 풀.
     * - CODEF OCR 호출과 행안부 검증 호출이 같은 스레드에서 순차 실행되며, 둘 다 외부 API 응답을 기다리는 I/O 대기 작업이다
     *   (Tesseract 로컬 OCR 시절엔 CPU 집중 작업이었으나 #68에서 CODEF API 호출로 교체되며 성격이 바뀌었다)
     * - corePoolSize(10): I/O 대기가 대부분이라 스레드를 더 많이 띄워도 CPU 부담이 적음
     * - maxPoolSize(30): 동시 요청 폭증(예: 가입 이벤트) 시 최대 30개까지 확장
     * - queueCapacity(50): 대기 큐 초과 시 RejectedExecutionException 발생 → 호출부에서 처리
     * - keepAliveSeconds(60): 유휴 스레드 60초 후 회수
     */
    @Bean(name = "ocrExecutor")
    public Executor ocrExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ocr-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
