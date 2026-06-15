package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.SavingProduct;

public record SavingProductSummaryRes(
        Long id,
        String name,
        String bankName,
        Double interestRate,
        Integer periodMonth,
        Long minAmount
) {

    public static SavingProductSummaryRes from(SavingProduct
                                                       savingProduct) {
        return new SavingProductSummaryRes(
                savingProduct.getId(),
                savingProduct.getName(),
                savingProduct.getBankName(),
                savingProduct.getInterestRate(),
                savingProduct.getPeriodMonth(),
                savingProduct.getMinAmount()
        );
    }
}
