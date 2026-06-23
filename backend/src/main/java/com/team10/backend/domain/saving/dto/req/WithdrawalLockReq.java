package com.team10.backend.domain.saving.dto.req;

import com.team10.backend.domain.saving.type.SavingProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WithdrawalLockReq(
        @Schema(description = "저축 상품 타입", example = "DEPOSIT")
        @NotNull
        SavingProductType savingType,

        @Schema(description = "출금 제한 여부", example = "true")
        @NotNull
        Boolean lockYn,

        @Schema(description = "출금 제한 사유", example = "목표 저축을 위해 만기 전 출금 제한")
        @Size(max = 255)
        String reason
) {
}
