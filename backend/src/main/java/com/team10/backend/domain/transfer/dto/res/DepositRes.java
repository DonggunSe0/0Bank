package com.team10.backend.domain.transfer.dto.res;

import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.type.TransactionType;

import java.time.LocalDateTime;

public record DepositRes(
        Long transactionId,
        Long accountId,
        TransactionType type,
        Long amount,
        Long balanceBefore,
        Long balanceAfter,
        String memo,
        LocalDateTime transactedAt
) {
    public static DepositRes from(TransactionHistory transactionHistory) {
        return new DepositRes(
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
