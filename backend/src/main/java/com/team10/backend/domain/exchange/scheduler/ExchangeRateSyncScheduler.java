package com.team10.backend.domain.exchange.scheduler;

import com.team10.backend.domain.exchange.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private final ExchangeRateService exchangeRateService;

    // 한 번 작업이 끝나고 1분 뒤에 ExchangeRateService.syncCurrentRates() 호출
    @Scheduled(
            fixedDelayString = "${exchange-rate.sync.fixed-delay-ms}",
            initialDelayString = "${exchange-rate.sync.fixed-delay-ms}" // 1분 뒤 부터 반복 시작
    )
    public void syncCurrentRates() {
        try {
            exchangeRateService.syncCurrentRates();
            log.info("환율 동기화 스케줄러 실행 완료");
        } catch (RuntimeException e) {
            log.warn("환율 동기화 스케줄러 실행 실패", e);
        }
    }
}
