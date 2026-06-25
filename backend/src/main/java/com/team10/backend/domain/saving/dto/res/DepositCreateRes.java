package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.type.DepositStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record DepositCreateRes(
        @Schema(description = "예금 가입 ID", example = "1")
        Long depositId,

        @Schema(description = "예금 상태", example = "ACTIVE")
        DepositStatus status,

        @Schema(description = "예치 원금", example = "1000000")
        Long principal,

        @Schema(description = "만기일", example = "2027-06-16")
        LocalDate maturityDate,

        @Schema(description = "예상 이자", example = "35000")
        Long expectedInterest
) {
    public static DepositCreateRes from(Deposit deposit) {

        return new DepositCreateRes(
                deposit.getId(),
                deposit.getStatus(),
                deposit.getPrincipal(),
                deposit.getMaturityDate(),
                deposit.getExpectedInterest()
        );
    }
}
