package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.type.DepositStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record DepositDetailRes(
        @Schema(description = "예금 가입 ID", example = "1")
        Long depositId,

        @Schema(description = "예금 상품명", example = "정기예금")
        String productName,

        @Schema(description = "은행명", example = "국민은행")
        String bankName,

        @Schema(description = "예치 원금", example = "1000000")
        Long principal,

        @Schema(description = "가입 당시 금리", example = "3.5")
        Double interestRate,

        @Schema(description = "예상 이자", example = "35000")
        Long expectedInterest,

        @Schema(description = "만기일", example = "2027-06-16")
        LocalDate maturityDate,

        @Schema(description = "예금 상태", example = "ACTIVE")
        DepositStatus status
) {
    public static DepositDetailRes from(Deposit deposit) {

        return new DepositDetailRes(
                deposit.getId(),
                deposit.getSavingProduct().getName(),
                deposit.getSavingProduct().getBankName(),
                deposit.getPrincipal(),
                deposit.getInterestRate(),
                deposit.getExpectedInterest(),
                deposit.getMaturityDate(),
                deposit.getStatus()
        );
    }
}
