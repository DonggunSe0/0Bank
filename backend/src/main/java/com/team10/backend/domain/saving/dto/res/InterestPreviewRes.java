package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.type.SavingProductType;
import io.swagger.v3.oas.annotations.media.Schema;


public record InterestPreviewRes(
        @Schema(description = "저축 가입 ID", example = "1")
        Long savingId,

        @Schema(description = "저축 상품 타입", example = "DEPOSIT")
        SavingProductType savingType,

        @Schema(description = "원금 또는 납입 금액", example = "1000000")
        Long principal,

        @Schema(description = "금리", example = "3.5")
        Double interestRate,

        @Schema(description = "예상 이자", example = "35000")
        Long expectedInterest,

        @Schema(description = "만기 예상 수령액", example = "1035000")
        Long expectedTotalAmount
) {
    public static InterestPreviewRes fromDeposit(
            Deposit deposit,
            Long expectedTotalAmount
    ) {

        return new InterestPreviewRes(
                deposit.getId(),
                SavingProductType.DEPOSIT,
                deposit.getPrincipal(),
                deposit.getInterestRate(),
                deposit.getExpectedInterest(),
                expectedTotalAmount
        );
    }

    public static InterestPreviewRes fromInstallment(
            Installment installment,
            Long expectedInterest,
            Long expectedTotalAmount
    ) {

        return new InterestPreviewRes(
                installment.getId(),
                SavingProductType.INSTALLMENT,
                installment.getTargetAmount(),
                installment.getInterestRate(),
                expectedInterest,
                expectedTotalAmount
        );
    }
}
