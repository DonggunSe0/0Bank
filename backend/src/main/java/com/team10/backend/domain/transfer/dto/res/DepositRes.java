package com.team10.backend.domain.transfer.dto.res;

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
}
