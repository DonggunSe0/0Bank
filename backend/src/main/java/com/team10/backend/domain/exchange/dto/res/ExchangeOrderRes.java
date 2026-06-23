package com.team10.backend.domain.exchange.dto.res;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.exchange.entity.ExchangeOrder;
import com.team10.backend.domain.exchange.entity.FxWallet;
import com.team10.backend.domain.exchange.type.ExchangeDirection;
import com.team10.backend.domain.exchange.type.ExchangeOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "환전 주문 응답")
public record ExchangeOrderRes(
        @Schema(description = "환전 주문 ID", example = "1")
        Long exchangeOrderId,

        @Schema(description = "환전 견적 ID", example = "1")
        Long exchangeQuoteId,

        @Schema(description = "환전 방향", example = "BUY")
        ExchangeDirection direction,

        @Schema(description = "환전 주문 상태", example = "COMPLETED")
        ExchangeOrderStatus status,

        @Schema(description = "원화 계좌 ID", example = "1")
        Long krwAccountId,

        @Schema(description = "외화 지갑 ID", example = "1")
        Long fxWalletId,

        @Schema(description = "출금 통화 기준 환전 금액", example = "100000")
        BigDecimal fromAmount,

        @Schema(description = "입금 통화 기준 환전 결과 금액", example = "72.50")
        BigDecimal toAmount,

        @Schema(description = "적용 환율", example = "1379.31")
        BigDecimal appliedRate,

        @Schema(description = "수수료율", example = "0.001")
        BigDecimal feeRate,

        @Schema(description = "환전 수수료", example = "100.00")
        BigDecimal fee,

        @Schema(description = "주문 생성 시각", example = "2026-06-17T10:00:00")
        LocalDateTime createdAt,

        @Schema(description = "주문 완료 시각", example = "2026-06-17T10:00:01")
        LocalDateTime completedAt
) {

    public static ExchangeOrderRes from(ExchangeOrder exchangeOrder) {
        return new ExchangeOrderRes(
                exchangeOrder.getId(),
                exchangeOrder.getExchangeQuote().getId(),
                exchangeOrder.getDirection(),
                exchangeOrder.getStatus(),
                getAccountId(exchangeOrder.getKrwAccount()),
                getFxWalletId(exchangeOrder.getFxWallet()),
                exchangeOrder.getFromAmount(),
                exchangeOrder.getToAmount(),
                exchangeOrder.getAppliedRate(),
                exchangeOrder.getFeeRate(),
                exchangeOrder.getFee(),
                exchangeOrder.getCreatedAt(),
                exchangeOrder.getCompletedAt()
        );
    }

    private static Long getAccountId(Account account) {
        return account == null ? null : account.getId();
    }

    private static Long getFxWalletId(FxWallet fxWallet) {
        return fxWallet == null ? null : fxWallet.getId();
    }
}
