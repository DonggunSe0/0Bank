package com.team10.backend.domain.exchange.dto.res;

import com.team10.backend.domain.exchange.entity.ExchangeRate;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "환율 응답")
public record ExchangeRateRes(
        @Schema(description = "환율 ID", example = "1")
        Long exchangeRateId,

        @Schema(description = "통화 코드", example = "USD")
        CurrencyCode currencyCode,

        @Schema(description = "기준 환율", example = "1379.31")
        BigDecimal basePrice,

        @Schema(description = "환율 기준 통화 단위", example = "1")
        Integer currencyUnit,

        @Schema(description = "환율 고시 시각", example = "2026-06-17T10:00:00")
        LocalDateTime rateAt
) {

    public static ExchangeRateRes from(ExchangeRate exchangeRate) {
        return new ExchangeRateRes(
                exchangeRate.getId(),
                exchangeRate.getCurrency().getCurrencyCode(),
                exchangeRate.getBasePrice(),
                exchangeRate.getCurrencyUnit(),
                exchangeRate.getRateAt()
        );
    }
}
