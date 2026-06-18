package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;

public record InstallmentDetailRes(
        @Schema(description = "적금 가입 ID", example = "1")
        Long installmentId,

        @Schema(description = "적금 상품명", example = "정기적금")
        String productName,

        @Schema(description = "은행명", example = "국민은행")
        String bankName,

        @Schema(description = "월 납입액", example = "100000")
        Long monthlyAmount,

        @Schema(description = "현재까지 납입한 금액", example = "100000")
        Long paidAmount,

        @Schema(description = "목표 금액", example = "1200000")
        Long targetAmount,

        @Schema(description = "목표 대비 진행률", example = "8")
        Long progressRate,

        @Schema(description = "만기일", example = "2027-06-16")
        LocalDate maturityDate,

        @Schema(description = "적금 상태", example = "ACTIVE")
        InstallmentStatus status

) {
    public static InstallmentDetailRes from(Installment installment) {
        Objects.requireNonNull(installment, "installment는 null일 수 없습니다.");

        return new InstallmentDetailRes(
                installment.getId(),
                installment.getSavingProduct().getName(),
                installment.getSavingProduct().getBankName(),
                installment.getMonthlyAmount(),
                installment.getPaidAmount(),
                installment.getTargetAmount(),
                installment.getProgressRate(),
                installment.getMaturityDate(),
                installment.getStatus()
        );
    }

}
