package com.team10.backend.domain.investment.account.dto.req;

import com.team10.backend.domain.investment.type.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InvestmentAccountCreateReq(
        @Schema(description = "투자 계좌 별칭", nullable = true)
        @Size(max = 50, message = "투자 계좌 별칭은 50자 이하여야 합니다.")
        String nickname,

        @Schema(description = "투자 계좌 비밀번호. 숫자 6자리")
        @NotBlank(message = "투자 계좌 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "투자 계좌 비밀번호는 숫자 6자리여야 합니다.")
        String accountPassword,

        @Schema(description = "투자 계좌 개설 인증키")
        @NotBlank(message = "투자 계좌 개설 인증키는 필수입니다.")
        String verificationKey,

        @Schema(description = "투자 계좌 화폐 종류", allowableValues = {"KRW"})
        @NotNull(message = "투자 계좌 화폐는 필수입니다.")
        CurrencyCode currencyCode
) {
}
