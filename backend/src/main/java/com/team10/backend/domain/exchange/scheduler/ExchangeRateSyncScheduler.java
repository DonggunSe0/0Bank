package com.team10.backend.domain.exchange.scheduler;

import com.team10.backend.domain.exchange.service.ExchangeRateService;
import com.team10.backend.global.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 테스트 환경에서는 스케줄러 비활성화
@ConditionalOnProperty(
        name = "exchange-rate.sync.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateSyncScheduler {

    private static final String LOCK_KEY = "lock:scheduler:exchange-rate-sync";
    private static final Duration LOCK_LEASE_TIME = Duration.ofSeconds(50);

    private final ExchangeRateService exchangeRateService;
    private final DistributedLockTemplate lockTemplate;

    // 한 번 작업이 끝나고 1분 뒤에 ExchangeRateService.syncCurrentRates() 호출
    @Scheduled(
            fixedDelayString = "${exchange-rate.sync.fixed-delay-ms}",
            initialDelayString = "${exchange-rate.sync.fixed-delay-ms}" // 1분 뒤 부터 반복 시작
    )
    public void syncCurrentRates() {
        try {
            // 멀티 인스턴스 환경에서는 모든 서버의 스케줄러가 동시에 실행될 수 있으므로,
            // 하나의 인스턴스만 환율 동기화를 수행하고 나머지는 정상적으로 건너뛴다.
            boolean executed = lockTemplate.tryExecuteWithLock(
                    LOCK_KEY,
                    LOCK_LEASE_TIME,
                    () -> {
                        exchangeRateService.syncCurrentRates();
                        log.info("환율 동기화 스케줄러 실행 완료");
                    }
            );

            if (!executed) {
                log.info("다른 인스턴스에서 환율 동기화 스케줄러가 실행 중이므로 이번 실행을 건너뜁니다.");
            }
        } catch (RuntimeException e) {
            log.warn("환율 동기화 스케줄러 실행 실패", e);
        }
    }
}
