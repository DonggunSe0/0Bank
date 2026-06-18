package com.team10.backend.domain.investment.account.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

public record InvestmentAccountOpenVerificationRes(
        @Schema(description = "투자 계좌 개설 인증키", example = "74a63775-d3bf-4da4-a7fa-05ee3375ceff")
        String verificationKey,

        @Schema(description = "인증키 유효시간(초)", example = "600")
        long expiresInSeconds
) {
}
