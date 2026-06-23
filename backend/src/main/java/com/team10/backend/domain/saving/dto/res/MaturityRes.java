package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.type.SavingProductType;
import io.swagger.v3.oas.annotations.media.Schema;


public record MaturityRes(
        @Schema(description = "저축 가입 ID", example = "1")
        Long savingId,

        @Schema(description = "저축 상품 타입", example = "DEPOSIT")
        SavingProductType savingType,

        @Schema(description = "지급 원금", example = "1000000")
        Long principalAmount,

        @Schema(description = "확정 이자", example = "35000")
        Long interestAmount,

        @Schema(description = "만기 지급 금액", example = "1035000")
        Long payoutAmount,

        @Schema(description = "만기 처리 후 상태", example = "MATURED")
        String status
) {
    public static MaturityRes fromDeposit(
            Deposit deposit,
            Long interestAmount,
            Long payoutAmount
    ) {

        return new MaturityRes(
                deposit.getId(),
                SavingProductType.DEPOSIT,
                deposit.getPrincipal(),
                interestAmount,
                payoutAmount,
                deposit.getStatus().name()
        );
    }

    public static MaturityRes fromInstallment(
            Installment installment,
            Long interestAmount,
            Long payoutAmount
    ) {

        return new MaturityRes(
                installment.getId(),
                SavingProductType.INSTALLMENT,
                installment.getPaidAmount(),
                interestAmount,
                payoutAmount,
                installment.getStatus().name()
        );
    }
}
