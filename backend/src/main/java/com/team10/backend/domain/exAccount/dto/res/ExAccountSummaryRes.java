package com.team10.backend.domain.exAccount.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "외부 계좌 자산 요약 응답")
public record ExAccountSummaryRes(
        @Schema(description = "총 자산 금액", example = "5000000.00")
        BigDecimal totalAssetAmount,
        @Schema(description = "총 부채 금액", example = "1000000.00")
        BigDecimal totalDebtAmount,
        @Schema(description = "순자산 금액", example = "4000000.00")
        BigDecimal netAssetAmount,
        @Schema(description = "월 수입 금액", example = "3000000.00")
        BigDecimal monthlyIncomeAmount,
        @Schema(description = "월 지출 금액", example = "1800000.00")
        BigDecimal monthlyExpenseAmount,
        @Schema(description = "외부 계좌 수", example = "3")
        long accountCount,
        @Schema(description = "대출 계좌 수", example = "1")
        long loanCount
) {

}
