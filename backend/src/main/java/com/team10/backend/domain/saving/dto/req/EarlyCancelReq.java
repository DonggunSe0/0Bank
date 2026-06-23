package com.team10.backend.domain.saving.dto.req;

import com.team10.backend.domain.saving.type.SavingProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record EarlyCancelReq(
        @Schema(description = "저축 상품 타입", example = "DEPOSIT")
        @NotNull
        SavingProductType savingType
) {
}
