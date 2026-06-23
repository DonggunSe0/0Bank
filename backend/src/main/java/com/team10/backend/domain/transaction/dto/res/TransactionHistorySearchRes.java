package com.team10.backend.domain.transaction.dto.res;

import com.team10.backend.domain.transaction.type.TransactionDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "거래내역 목록 응답")
public record TransactionHistorySearchRes(
        @Schema(description = "거래내역 ID")
        Long transactionHistoryId,

        @Schema(description = "거래 상대명")
        String counterpartyName,

        @Schema(description = "거래 금액")
        Long amount,

        @Schema(description = "거래 후 잔액")
        Long balanceAfter,

        @Schema(description = "거래 일시")
        LocalDateTime transactedAt,

        @Schema(description = "입출금 방향", allowableValues = {"IN", "OUT"})
        TransactionDirection direction
) {
}
