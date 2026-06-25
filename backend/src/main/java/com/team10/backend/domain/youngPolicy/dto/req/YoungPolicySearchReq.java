package com.team10.backend.domain.youngPolicy.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "청년정책 필터링 검색 요청")
public record YoungPolicySearchReq(
        @Schema(description = "사용자 나이 (만 나이 기준)", example = "25")
        Integer age,

        @Schema(description = "지역 이름 (시도 단위 혹은 시군구 주소지)", example = "서울")
        String region,

        @Schema(description = "정책 카테고리 (대분류)", example = "금융･복지･문화")
        String category,

        @Schema(description = "검색 키워드 (정책명 혹은 설명에 포함)", example = "대출")
        String keyword
) {
}
