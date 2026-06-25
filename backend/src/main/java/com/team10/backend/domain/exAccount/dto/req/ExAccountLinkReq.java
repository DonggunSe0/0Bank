package com.team10.backend.domain.exAccount.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "외부 계좌 연동 요청")
public record ExAccountLinkReq(
        @Schema(description = "일회용 임시 연동 토큰", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "연동 토큰은 필수입니다.")
        String candidateToken,

        @Schema(description = "연동할 계좌 후보들의 인덱스 목록", example = "[0]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "연동할 계좌 인덱스는 최소 1개 이상이어야 합니다.")
        List<Integer> selectedIndexes
) {
}
