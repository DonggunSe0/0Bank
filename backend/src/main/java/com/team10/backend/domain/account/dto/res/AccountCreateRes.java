package com.team10.backend.domain.account.dto.res;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record AccountCreateRes(
        @Schema(description = "계좌 ID", example = "1")
        Long id,

        @Schema(description = "계좌번호", example = "031412345678")
        String accountNumber,

        @Schema(description = "계좌 별칭", example = "생활비 계좌")
        String nickname,

        @Schema(description = "계좌 타입", example = "DEPOSIT")
        AccountType accountType,

        @Schema(description = "계좌 잔액", example = "0")
        Long balance,

        @Schema(description = "계좌 상태", example = "ACTIVE")
        AccountStatus status,

        @Schema(description = "계좌 생성 일시", example = "2026-06-16T10:30:00")
        LocalDateTime createdAt
) {

    public static AccountCreateRes from(Account account) {

        return new AccountCreateRes(
                account.getId(),
                account.getAccountNumber(),
                account.getNickname(),
                account.getAccountType(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
