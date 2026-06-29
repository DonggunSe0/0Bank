package com.team10.backend.domain.exchange.scheduler;

import com.team10.backend.domain.exchange.service.ExchangeRateService;
import com.team10.backend.global.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "exchange-rate.sync.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ExchangeRateStartupSyncRunner {

    private static final String LOCK_KEY = "lock:scheduler:exchange-rate-sync";
    private static final Duration LOCK_LEASE_TIME = Duration.ofSeconds(50);

    private final ExchangeRateService exchangeRateService;
    private final DistributedLockTemplate lockTemplate;

    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        try {
            // 애플리케이션 시작 이벤트도 인스턴스마다 발생하므로,
            // 환율 동기화가 중복 실행되지 않도록 반복 스케줄러와 같은 락을 사용한다.
            boolean executed = lockTemplate.tryExecuteWithLock(
                    LOCK_KEY,
                    LOCK_LEASE_TIME,
                    () -> {
                        log.info("애플리케이션 시작 시 환율 동기화 시작");
                        exchangeRateService.syncCurrentRates();
                        log.info("애플리케이션 시작 시 환율 동기화 완료");
                    }
            );

            if (!executed) {
                log.info("다른 인스턴스에서 환율 동기화가 실행 중이므로 시작 시 동기화를 건너뜁니다.");
            }
        } catch (RuntimeException e) {
            log.warn("애플리케이션 시작 시 환율 동기화 실패", e);
        }
    }
}
