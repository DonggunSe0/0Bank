package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.type.SavingProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record WithdrawalLockRes(
        @Schema(description = "저축 가입 ID", example = "1")
        Long savingId,

        @Schema(description = "저축 상품 타입", example = "DEPOSIT")
        SavingProductType savingType,

        @Schema(description = "출금 제한 여부", example = "true")
        Boolean lockYn,

        @Schema(description = "출금 제한 사유", example = "긴급 출금 방지를 위해 제한")

        @Size(max = 255)
        String reason,

        @Schema(description = "출금 제한 설정 수정 일시", example = "2026-06-19T10:30:00")
        LocalDateTime updatedAt
) {
    public static WithdrawalLockRes fromDeposit(Deposit deposit) {

        return new WithdrawalLockRes(
                deposit.getId(),
                SavingProductType.DEPOSIT,
                deposit.isWithdrawalLocked(),
                deposit.getWithdrawalLockReason(),
                deposit.getUpdatedAt()
        );
    }

    public static WithdrawalLockRes fromInstallment(Installment installment) {

        return new WithdrawalLockRes(
                installment.getId(),
                SavingProductType.INSTALLMENT,
                installment.isWithdrawalLocked(),
                installment.getWithdrawalLockReason(),
                installment.getUpdatedAt()
        );
    }
}
