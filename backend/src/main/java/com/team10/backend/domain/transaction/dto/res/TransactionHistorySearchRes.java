package com.team10.backend.domain.transaction.dto.res;

import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "거래내역 목록 응답")
public record TransactionHistorySearchRes(
        @Schema(description = "거래내역 ID")
        Long transactionHistoryId,

        @Schema(description = "거래 유형")
        TransactionType type,

        @Schema(description = "거래 상대명")
        String counterpartyName,

        @Schema(description = "화면 표시용 거래명")
        String displayName,

        @Schema(description = "거래 금액")
        Long amount,

        @Schema(description = "거래 후 잔액")
        Long balanceAfter,

        @Schema(description = "거래 일시")
        LocalDateTime transactedAt,

        @Schema(description = "메모")
        String memo,

        @Schema(description = "입출금 방향", allowableValues = {"IN", "OUT"})
        TransactionDirection direction
) {
    public TransactionHistorySearchRes {
        if (!hasText(displayName)) {
            displayName = resolveDisplayName(type, direction, counterpartyName);
        }
    }

    public static String resolveDisplayName(
            TransactionType type,
            TransactionDirection direction,
            String counterpartyName
    ) {
        if (hasText(counterpartyName)) {
            return counterpartyName;
        }

        if (type == null) {
            return direction == TransactionDirection.IN ? "입금" : "출금";
        }

        return switch (type) {
            case DEPOSIT -> "입금";
            case TRANSFER -> direction == TransactionDirection.IN ? "계좌이체입금" : "계좌이체출금";
            case EXCHANGE -> "환전";
            case PAYMENT -> "결제";
            case SAVING_DEPOSIT_SIGNUP -> "예금 가입";
            case SAVING_INSTALLMENT_SIGNUP -> "적금 가입";
            case SAVING_CANCEL_REFUND -> direction == TransactionDirection.IN ? "예적금 해지입금" : "예적금 해지출금";
            case SAVING_MATURITY -> direction == TransactionDirection.IN ? "예적금 만기입금" : "예적금 만기출금";
            case INSTALLMENT_PAYMENT -> "적금 자동납입";
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
