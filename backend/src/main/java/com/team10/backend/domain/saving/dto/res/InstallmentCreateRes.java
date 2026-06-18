package com.team10.backend.domain.saving.dto.res;

import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Objects;

public record InstallmentCreateRes(
        @Schema(description = "적금 가입 ID", example = "1")
        Long installmentId,

        @Schema(description = "적금 상태", example = "ACTIVE")
        InstallmentStatus status,

        @Schema(description = "만기일", example = "2027-06-16")
        LocalDate maturityDate,

        @Schema(description = "목표 대비 진행률", example = "0")
        Long progressRate
) {
    public static InstallmentCreateRes from(Installment installment) {
        Objects.requireNonNull(installment, "installment는 null일 수 없습니다.");

        return new InstallmentCreateRes(
                installment.getId(),
                installment.getStatus(),
                installment.getMaturityDate(),
                installment.getProgressRate()
        );
    }
}
