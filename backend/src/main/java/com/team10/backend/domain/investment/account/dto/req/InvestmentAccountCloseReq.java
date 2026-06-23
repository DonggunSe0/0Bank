package com.team10.backend.domain.investment.account.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InvestmentAccountCloseReq(
        @Schema(description = "투자 계좌 비밀번호. 숫자 6자리")
        @NotBlank(message = "투자 계좌 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "투자 계좌 비밀번호는 숫자 6자리여야 합니다.")
        String accountPassword
) {
}
