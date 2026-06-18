package com.team10.backend.domain.exchange.dto.res;

import com.team10.backend.domain.exchange.entity.ExchangeQuote;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "환전 견적 응답")
public record ExchangeQuoteRes(
        @Schema(description = "환전 견적 ID", example = "1")
        Long exchangeQuoteId,

        @Schema(description = "출금 통화 코드", example = "KRW")
        CurrencyCode fromCurrencyCode,

        @Schema(description = "입금 통화 코드", example = "USD")
        CurrencyCode toCurrencyCode,

        @Schema(description = "출금 통화 기준 환전 금액", example = "100000")
        BigDecimal fromAmount,

        @Schema(description = "견적 적용 환율", example = "1379.31")
        BigDecimal rate,

        @Schema(description = "수수료율", example = "0.001")
        BigDecimal feeRate,

        @Schema(description = "예상 환전 수수료", example = "100.00")
        BigDecimal fee,

        @Schema(description = "예상 입금 금액", example = "72.50")
        BigDecimal expectedToAmount,

        @Schema(description = "견적 만료 시각", example = "2026-06-17T10:05:00")
        LocalDateTime expiredAt,

        @Schema(description = "견적 생성 시각", example = "2026-06-17T10:00:00")
        LocalDateTime createdAt
) {

    public static ExchangeQuoteRes from(ExchangeQuote exchangeQuote) {
        return new ExchangeQuoteRes(
                exchangeQuote.getId(),
                exchangeQuote.getFromCurrency().getCurrencyCode(),
                exchangeQuote.getToCurrency().getCurrencyCode(),
                exchangeQuote.getFromAmount(),
                exchangeQuote.getRate(),
                exchangeQuote.getFeeRate(),
                exchangeQuote.getFee(),
                exchangeQuote.getExpectedToAmount(),
                exchangeQuote.getExpiredAt(),
                exchangeQuote.getCreatedAt()
        );
    }
}
