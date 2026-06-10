package com.team10.backend.domain.account.dto.req;

import com.team10.backend.domain.account.type.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountCreateReq(
        @Size(max = 50, message = "계좌 별칭은 50자 이하여야 합니다.")
        String nickname,

        @NotNull(message="계좌 타입은 필수입니다.")
        AccountType accountType
){
}