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

    /**
     * 1원 송금(CODEF) 비동기 처리 전용 스레드 풀.
     * - 은행 응답 대기로 건당 최대 30초까지 블로킹되는 I/O 대기 작업 (CodefHttpServiceConfig의 BANK_TRANSFER_READ_TIMEOUT=30s)
     * - OCR과 별도 풀을 쓰는 이유: 1원송금 1건의 점유 시간이 OCR보다 훨씬 길어, 같은 풀을 공유하면
     *   1원송금 요청이 몰릴 때 OCR 처리가 함께 지연될 수 있다
     * - corePoolSize(5)/maxPoolSize(20): OCR보다 호출량이 적을 것으로 예상되는 단계라 더 작게 설정
     * - queueCapacity(50): 대기 큐 초과 시 RejectedExecutionException 발생 → 리스너에서 재시도 가능 상태로 복구
     */
    @Bean(name = "oneWonExecutor")
    public Executor oneWonExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("one-won-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
