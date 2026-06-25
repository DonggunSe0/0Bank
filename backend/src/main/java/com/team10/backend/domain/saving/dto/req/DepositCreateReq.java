package com.team10.backend.domain.saving.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepositCreateReq(
        @Schema(description = "예금 상품 ID", example = "1")
        @NotNull
        @Positive
        Long productId,

        @Schema(description = "출금 계좌 ID", example = "3")
        @NotNull
        @Positive
        Long withdrawAccountId,

        @Schema(description = "예금 가입 금액", example = "1000000")
        @NotNull
        @Positive
        Long amount
) {
}
