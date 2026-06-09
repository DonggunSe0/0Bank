package com.team10.backend.domain.transfer.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DepositReq(
        @NotNull
        Long accountId,

        @NotNull
        @Positive
        Long amount,

        @Size(max = 100)
        String memo
) {
}
