package com.team10.backend.domain.youngPolicy.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "청년정책 동기화 결과")
public record YoungPolicySyncRes(
        @Schema(description = "외부 API에서 가져온 정책 수", example = "100")
        int fetchedCount,

        @Schema(description = "DB에 새로 저장한 정책 수", example = "80")
        int createdCount,

        @Schema(description = "이미 존재해 갱신한 정책 수", example = "20")
        int updatedCount,

        @Schema(description = "정책번호 누락 등으로 저장하지 않은 정책 수", example = "0")
        int skippedCount
) {
}
