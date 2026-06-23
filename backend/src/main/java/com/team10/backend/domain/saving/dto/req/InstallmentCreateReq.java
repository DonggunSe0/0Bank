package com.team10.backend.domain.saving.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InstallmentCreateReq(
        @Schema(description = "적금 상품 ID", example = "1")
        @NotNull
        @Positive
        Long productId,

        @Schema(description = "출금 계좌 ID", example = "1")
        @NotNull
        @Positive
        Long withdrawAccountId,

        @Schema(description = "월 납입액", example = "100000")
        @NotNull
        @Positive
        Long monthlyAmount,

        @Schema(description = "목표 금액", example = "1200000")
        @NotNull
        @Positive
        Long targetAmount,

        @Schema(description = "자동이체 여부", example = "true")
        @NotNull
        Boolean autoTransferYn

) {
}
