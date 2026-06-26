package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.SavingProduct;
import com.team10.backend.domain.saving.type.SavingProductType;
import io.swagger.v3.oas.annotations.media.Schema;

public record SavingProductSummaryRes(
        @Schema(description = "저축 상품 ID", example = "1")
        Long id,

        @Schema(description = "저축 상품명", example = "정기예금")
        String name,

        @Schema(description = "은행명", example = "국민은행")
        String bankName,

        @Schema(description = "은행 코드", example = "KB")
        String bankCode,

        @Schema(description = "저축 상품 타입", example = "DEPOSIT")
        SavingProductType type,

        @Schema(description = "기본 금리", example = "3.5")
        Double interestRate,

        @Schema(description = "가입 기간 개월 수", example = "12")
        Integer periodMonth,

        @Schema(description = "최소 가입 금액", example = "1000000")
        Long minAmount,

        @Schema(description = "최대 가입 금액", example = "50000000")
        Long maxAmount,

        @Schema(description = "월 납입 한도", example = "1000000")
        Long monthlyLimit,

        @Schema(description = "가입 조건", example = "개인 고객 가입 가능")
        String terms
) {

    public static SavingProductSummaryRes from(SavingProduct savingProduct) {
        return new SavingProductSummaryRes(
                savingProduct.getId(),
                savingProduct.getName(),
                savingProduct.getBankName(),
                savingProduct.getBankCode(),
                savingProduct.getType(),
                savingProduct.getInterestRate(),
                savingProduct.getPeriodMonth(),
                savingProduct.getMinAmount(),
                savingProduct.getMaxAmount(),
                savingProduct.getMonthlyLimit(),
                savingProduct.getTerms()
        );
    }
}
