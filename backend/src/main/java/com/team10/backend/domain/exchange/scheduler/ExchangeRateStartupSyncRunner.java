package com.team10.backend.domain.exchange.scheduler;

import com.team10.backend.domain.exchange.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "exchange-rate.sync.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ExchangeRateStartupSyncRunner {

    private final ExchangeRateService exchangeRateService;

    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        try {
            log.info("애플리케이션 시작 시 환율 동기화 시작");
            exchangeRateService.syncCurrentRates();
            log.info("애플리케이션 시작 시 환율 동기화 완료");
        } catch (RuntimeException e) {
            log.warn("애플리케이션 시작 시 환율 동기화 실패", e);
        }
    }
}
