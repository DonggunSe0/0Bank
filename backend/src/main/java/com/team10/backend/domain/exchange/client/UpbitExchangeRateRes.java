package com.team10.backend.domain.exchange.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpbitExchangeRateRes(
        String currencyCode, // JPY
        String currencyName, // 엔
        String country,      // 일본
        LocalDate date,      // 2026-06-13
        LocalTime time,      // 12:14:12 -> rateAt = response.date + response.time 으로 생성
        BigDecimal basePrice,// 948.35
        Integer currencyUnit // 100 (100원 기준이라는 뜻), 대부분 1 (1원 기준 환율)
) {
}
