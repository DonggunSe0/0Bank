package com.team10.backend.domain.saving.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        Long amount,

        @Schema(description = "출금 계좌 비밀번호. 숫자 6자리", example = "123456")
        @NotBlank(message = "계좌 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "계좌 비밀번호는 숫자 6자리여야 합니다.")
        String accountPassword
) {
}
