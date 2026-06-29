package com.team10.backend.domain.codef.exAccount.dto.internal;

import com.team10.backend.domain.exAccount.type.ExAccountTransactionDirection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CodefExAccountTransactionSnapshot(
        String transactionKey,
        LocalDateTime transactedAt,
        ExAccountTransactionDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String counterpartyName,
        String memo,
        String rawCategory
) {
}
