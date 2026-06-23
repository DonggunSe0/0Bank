package com.team10.backend.domain.exchange.scheduler;

import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.repository.CurrencyRepository;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CurrencyInitializer {

    private final CurrencyRepository currencyRepository;

    @Order(1) // 앱 시작 시: KRW Currency 보장 -> 외화 환율 동기화 실행
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void initializeKrwCurrency() {
        // 애플리케이션 실행 후 KRW가 없는 경우에만 생성
        currencyRepository.findByCurrencyCode(CurrencyCode.KRW)
                .orElseGet(() -> currencyRepository.save(
                        Currency.create(
                                CurrencyCode.KRW,
                                "원",
                                "대한민국",
                                0
                        )
                ));
    }
}
