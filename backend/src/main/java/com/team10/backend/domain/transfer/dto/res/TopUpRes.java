package com.team10.backend.domain.transfer.dto.res;

import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.type.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "입금 응답")
public record TopUpRes(
        @Schema(description = "거래내역 ID", example = "10")
        Long transactionId,

        @Schema(description = "입금 대상 계좌 ID", example = "1")
        Long accountId,

        @Schema(description = "거래 유형", example = "DEPOSIT")
        TransactionType type,

        @Schema(description = "입금 금액", example = "100000")
        Long amount,

        @Schema(description = "입금 전 잔액", example = "0")
        Long balanceBefore,

        @Schema(description = "입금 후 잔액", example = "100000")
        Long balanceAfter,

        @Schema(description = "입금 메모", example = "초기 입금")
        String memo,

        @Schema(description = "거래 발생 시각", example = "2026-06-17T10:00:00")
        LocalDateTime transactedAt
) {
    public static TopUpRes from(TransactionHistory transactionHistory) {
        return new TopUpRes(
                transactionHistory.getId(),             // 거래내역ID
                transactionHistory.getAccount().getId(),// 입금 대상 계좌ID
                transactionHistory.getType(),           // 거래유형 (입금)
                transactionHistory.getAmount(),         // 입금액
                transactionHistory.getBalanceBefore(),  // 입금 전 잔액
                transactionHistory.getBalanceAfter(),   // 입금 후 잔액
                transactionHistory.getMemo(),           // 입금 메모
                transactionHistory.getTransactedAt()    // 거래 발생 시각
        );
    }
}
