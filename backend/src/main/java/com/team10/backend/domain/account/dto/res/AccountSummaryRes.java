package com.team10.backend.domain.account.dto.res;

import com.team10.backend.domain.account.type.AccountStatus;
import java.time.LocalDateTime;

public record AccountSummaryRes (
        Long id,
        String accountNumber,
        String nickname,
        Long balance,
        AccountStatus status,
        LocalDateTime createdAt
){
}