package com.team10.backend.domain.transaction.dto.res;

import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "거래내역 상세 응답")
public record TransactionHistoryDetailRes(
        @Schema(description = "거래내역 ID")
        Long transactionHistoryId,

	        @Schema(
	                description = "거래 유형",
	                allowableValues = {
	                        "DEPOSIT",
	                        "TRANSFER",
	                        "PAYMENT",
	                        "EXCHANGE",
	                        "SAVING_DEPOSIT_SIGNUP",
	                        "SAVING_INSTALLMENT_SIGNUP",
	                        "SAVING_CANCEL_REFUND",
	                        "SAVING_MATURITY",
	                        "INSTALLMENT_PAYMENT"
	                }
	        )
        TransactionType type,

        @Schema(description = "입출금 방향", allowableValues = {"IN", "OUT"})
        TransactionDirection direction,

        @Schema(description = "거래 금액")
        Long amount,

        @Schema(description = "거래 후 잔액")
        Long balanceAfter,

        @Schema(description = "거래 상대명")
        String counterpartyName,

        @Schema(description = "화면 표시용 거래명")
        String displayName,

        @Schema(description = "거래 메모", nullable = true)
        String memo,

        @Schema(description = "거래 일시")
        LocalDateTime transactedAt
) {

    public static TransactionHistoryDetailRes from(TransactionHistory transactionHistory) {
        return new TransactionHistoryDetailRes(
                transactionHistory.getId(),
                transactionHistory.getType(),
                transactionHistory.getDirection(),
                transactionHistory.getAmount(),
                transactionHistory.getBalanceAfter(),
                transactionHistory.getCounterpartyName(),
                TransactionHistorySearchRes.resolveDisplayName(
                        transactionHistory.getType(),
                        transactionHistory.getDirection(),
                        transactionHistory.getCounterpartyName()
                ),
                transactionHistory.getMemo(),
                transactionHistory.getTransactedAt()
        );
    }
}
