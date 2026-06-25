package com.team10.backend.domain.exchange.calculator;

import java.math.BigDecimal;

public record QuoteCalculation(
        BigDecimal rate,                // 견적에 적용된 환율
        BigDecimal feeRate,             // 환전 수수료율
        BigDecimal fee,                 // 환전 수수료
        BigDecimal expectedToAmount     // 견적 결과값
) {
}
