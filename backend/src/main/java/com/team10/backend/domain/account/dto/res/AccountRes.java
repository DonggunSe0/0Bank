package com.team10.backend.domain.account.dto.res;

import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import java.time.LocalDateTime;

public record AccountRes (
        Long id,
        String accountNumber,
        String nickname,
        AccountType accountType,
        Long balance,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
){
}