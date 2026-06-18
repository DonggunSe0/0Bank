package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.type.DepositStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

public record DepositSummaryRes(
        @Schema(description = "예금 가입 ID", example = "1")
        Long depositId,

        @Schema(description = "예금 상품명", example = "정기예금")
        String productName,

        @Schema(description = "은행명", example = "국민은행")
        String bankName,

        @Schema(description = "예치 원금", example = "1000000")
        Long principal,

        @Schema(description = "예금 상태", example = "ACTIVE")
        DepositStatus status
) {

    public static DepositSummaryRes from(Deposit deposit) {
        Objects.requireNonNull(deposit, "deposit는 null일 수 없습니다.");

        return new DepositSummaryRes(
                deposit.getId(),
                deposit.getSavingProduct().getName(),
                deposit.getSavingProduct().getBankName(),
                deposit.getPrincipal(),
                deposit.getStatus()
        );
    }
}
