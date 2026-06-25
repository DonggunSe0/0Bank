package com.team10.backend.domain.youngPolicy.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "청년정책 동기화 요청")
public record YoungPolicyReq(
        @Schema(description = "청년센터 OpenAPI 페이지 번호", example = "1")
        Integer pageNum,

        @Schema(description = "한 번에 가져올 정책 개수", example = "100")
        Integer pageSize
) {
}
