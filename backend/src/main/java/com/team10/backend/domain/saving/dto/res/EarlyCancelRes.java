package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.exception.SavingErrorCode;
import com.team10.backend.domain.saving.type.SavingProductType;
import com.team10.backend.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;

public record EarlyCancelRes(
        @Schema(description = "저축 가입 ID", example = "1")
        Long savingId,

        @Schema(description = "저축 상품 타입", example = "DEPOSIT")
        SavingProductType savingType,

        @Schema(description = "반환 원금", example = "1000000")
        Long principalAmount,

        @Schema(description = "중도 해지 이자", example = "17500")
        Long interestAmount,

        @Schema(description = "반환 금액", example = "1017500")
        Long refundAmount,

        @Schema(description = "해지 후 상태", example = "CANCELLED")
        String status
) {
    public static EarlyCancelRes fromDeposit(
            Deposit deposit,
            Long interestAmount,
            Long refundAmount
    ) {
        if (deposit == null) {
            throw new BusinessException(SavingErrorCode.DEPOSIT_NOT_FOUND);
        }

        return new EarlyCancelRes(
                deposit.getId(),
                SavingProductType.DEPOSIT,
                deposit.getPrincipal(),
                interestAmount,
                refundAmount,
                deposit.getStatus().name()
        );
    }

    public static EarlyCancelRes fromInstallment(
            Installment installment,
            Long interestAmount,
            Long refundAmount
    ) {
        if (installment == null) {
            throw new BusinessException(SavingErrorCode.INSTALLMENT_NOT_FOUND);
        }

        return new EarlyCancelRes(
                installment.getId(),
                SavingProductType.INSTALLMENT,
                installment.getPaidAmount(),
                interestAmount,
                refundAmount,
                installment.getStatus().name()
        );
    }
}
