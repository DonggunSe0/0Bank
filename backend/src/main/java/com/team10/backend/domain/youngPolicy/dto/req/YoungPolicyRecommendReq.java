package com.team10.backend.domain.youngPolicy.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "RAG 기반 청년정책 추천 요청")
public record YoungPolicyRecommendReq(
        @Schema(description = "사용자 나이 (만 나이 기준)", example = "25")
        Integer age,

        @Schema(description = "지역 이름 (시도 단위 혹은 시군구 주소지)", example = "서울")
        String region,

        @Schema(description = "정책 카테고리 (대분류)", example = "금융･복지･문화")
        String category,

        @NotBlank(message = "추천에 필요한 질문이나 고민 내용을 입력해주세요.")
        @Schema(description = "사용자의 요구사항/고민/관심사 내용", example = "대학생인데 월세나 전세자금 대출을 지원받고 싶어요.")
        String query
) {
}
