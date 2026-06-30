package com.team10.backend.domain.account.repository;

import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;

public record AccountTransferVerification(
        Long id,
        AccountType accountType,
        AccountStatus status,
        String accountPasswordHash
) {
}
