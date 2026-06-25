package com.team10.backend.domain.codef.exAccount.dto.internal;

import com.team10.backend.domain.exAccount.type.ExAccountType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CodefExAccountSnapshot(
        String organization,
        String accountNumber,
        String accountName,
        String accountAlias,
        ExAccountType assetType,
        BigDecimal balance,
        BigDecimal withdrawableAmount,
        LocalDate openedAt,
        LocalDate maturityAt,
        LocalDate lastTransactionAt
) {
}
