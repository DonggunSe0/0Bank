package com.team10.backend.domain.investment.client.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KisStockPriceSnapshot(
        String stockCode,
        Long currentPrice,
        Long changePrice,
        BigDecimal changeRate,
        Long volume,
        LocalDateTime priceAt
) {
}
