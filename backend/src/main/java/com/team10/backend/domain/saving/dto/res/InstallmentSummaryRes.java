package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;


public record InstallmentSummaryRes(
        @Schema(description = "적금 가입 ID", example = "1")
        Long installmentId,

        @Schema(description = "적금 상품명", example = "정기적금")
        String productName,

        @Schema(description = "은행명", example = "국민은행")
        String bankName,

        @Schema(description = "현재까지 납입한 금액", example = "100000")
        Long paidAmount,

        @Schema(description = "목표 대비 진행률", example = "8")
        Long progressRate,

        @Schema(description = "적금 상태", example = "ACTIVE")
        InstallmentStatus status
) {
    public static InstallmentSummaryRes from(Installment installment) {

        return new InstallmentSummaryRes(
                installment.getId(),
                installment.getSavingProduct().getName(),
                installment.getSavingProduct().getBankName(),
                installment.getPaidAmount(),
                installment.getProgressRate(),
                installment.getStatus()
        );
    }
}
