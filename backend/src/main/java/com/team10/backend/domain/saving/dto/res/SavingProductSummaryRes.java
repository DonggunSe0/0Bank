package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.SavingProduct;
import io.swagger.v3.oas.annotations.media.Schema;

public record SavingProductSummaryRes(
        @Schema(description = "저축 상품 ID", example = "1")
        Long id,

        @Schema(description = "저축 상품명", example = "정기예금")
        String name,

        @Schema(description = "은행명", example = "국민은행")
        String bankName,

        @Schema(description = "기본 금리", example = "3.5")
        Double interestRate,

        @Schema(description = "가입 기간 개월 수", example = "12")
        Integer periodMonth
) {

    public static SavingProductSummaryRes from(SavingProduct savingProduct) {
        return new SavingProductSummaryRes(
                savingProduct.getId(),
                savingProduct.getName(),
                savingProduct.getBankName(),
                savingProduct.getInterestRate(),
                savingProduct.getPeriodMonth()
        );
    }
}
