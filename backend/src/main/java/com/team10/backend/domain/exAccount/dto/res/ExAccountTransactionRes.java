package com.team10.backend.domain.exAccount.dto.res;

import com.team10.backend.domain.exAccount.type.ExAccountTransactionDirection;
import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "외부 계좌 거래내역 응답")
public record ExAccountTransactionRes(
        @Schema(description = "외부 계좌 거래내역 ID", example = "1")
        Long id,
        @Schema(description = "외부 계좌 ID", example = "10")
        Long exAccountId,
        @Schema(description = "거래 일시", example = "2026-06-18T14:30:00")
        LocalDateTime transactedAt,
        @Schema(description = "입출금 방향", example = "OUT", allowableValues = {"IN", "OUT"})
        ExAccountTransactionDirection direction,
        @Schema(description = "거래 금액", example = "45000.00")
        BigDecimal amount,
        @Schema(description = "거래 후 잔액", example = "1455000.00", nullable = true)
        BigDecimal balanceAfter,
        @Schema(description = "거래 상대명", example = "스타벅스", nullable = true)
        String counterpartyName,
        @Schema(description = "거래 메모", example = "카드 결제", nullable = true)
        String memo,
        @Schema(description = "외부기관 원본 거래 카테고리", example = "식비", nullable = true)
        String rawCategory
) {
    public static ExAccountTransactionRes from(ExAccountTransaction transaction) {
        return new ExAccountTransactionRes(
                transaction.getId(),
                transaction.getExAccount().getId(),
                transaction.getTransactedAt(),
                transaction.getDirection(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCounterpartyName(),
                transaction.getMemo(),
                transaction.getRawCategory()
        );
    }
}

