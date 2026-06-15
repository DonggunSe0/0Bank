package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.SavingProduct;
import com.team10.backend.domain.saving.type.SavingProductType;

public record SavingProductRes(
        Long id,
        String name,
        String bankName,
        String bankCode,
        SavingProductType type,
        Double interestRate,
        Integer periodMonth,
        Long minAmount,
        Long maxAmount,
        Long monthlyLimit,
        String terms
) {

    public static SavingProductRes from(SavingProduct savingProduct) {
        return new SavingProductRes(
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