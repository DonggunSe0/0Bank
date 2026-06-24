package com.team10.backend.domain.account.dto.res;

import com.team10.backend.domain.account.entity.Account;
import io.swagger.v3.oas.annotations.media.Schema;

public record AccountPasswordRes(
        @Schema(description = "계좌 ID", example = "1")
        Long accountId,

        @Schema(description = "계좌 비밀번호 설정 여부", example = "true")
        Boolean passwordSet
) {

    public static AccountPasswordRes from(Account account) {
        return new AccountPasswordRes(
                account.getId(),
                account.getAccountPasswordHash() != null
        );
    }
}
