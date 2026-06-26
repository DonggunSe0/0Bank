package com.team10.backend.domain.saving.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        Boolean autoTransferYn,

        @Schema(description = "출금 계좌 비밀번호. 숫자 6자리", example = "123456")
        @NotBlank(message = "계좌 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "계좌 비밀번호는 숫자 6자리여야 합니다.")
        String accountPassword

) {
}
