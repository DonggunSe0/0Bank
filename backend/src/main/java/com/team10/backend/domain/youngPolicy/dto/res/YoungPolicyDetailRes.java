package com.team10.backend.domain.youngPolicy.dto.res;

import com.team10.backend.domain.youngPolicy.entity.YoungPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "청년정책 상세 응답")
public record YoungPolicyDetailRes(
        @Schema(description = "청년정책 ID", example = "1")
        Long id,

        @Schema(description = "외부 정책번호", example = "20260615005400213234")
        String policyId,

        @Schema(description = "정책명", example = "2026 북구청춘페스타 추진기획단 모집(~6.14.)")
        String title,

        @Schema(description = "정책 설명", example = "청년축제 행사를 함께 기획할 청년을 모집합니다.")
        String description,

        @Schema(description = "정책 대분류", example = "참여･기반")
        String category,

        @Schema(description = "정책 중분류", example = "청년참여")
        String subCategory,

        @Schema(description = "지원 최소 나이", example = "19")
        Integer minAge,

        @Schema(description = "지원 최대 나이", example = "39")
        Integer maxAge,

        @Schema(description = "지역 코드", example = "29170")
        String regionCode,

        @Schema(description = "취업요건 코드", example = "0013010")
        String jobCode,

        @Schema(description = "신청 기간", example = "20260527 ~ 20260614")
        String applyPeriod,

        @Schema(description = "신청 URL", example = "https://example.com")
        String applyUrl,

        @Schema(description = "신청 방법", example = "북구청년센터 홈페이지 온라인 신청")
        String applyMethod
) {
    public static YoungPolicyDetailRes from(YoungPolicy youngPolicy) {
        return new YoungPolicyDetailRes(
                youngPolicy.getId(),
                youngPolicy.getPolicyId(),
                youngPolicy.getTitle(),
                youngPolicy.getDescription(),
                youngPolicy.getCategory(),
                youngPolicy.getSubCategory(),
                youngPolicy.getMinAge(),
                youngPolicy.getMaxAge(),
                youngPolicy.getRegionCode(),
                youngPolicy.getJobCode(),
                youngPolicy.getApplyPeriod(),
                youngPolicy.getApplyUrl(),
                youngPolicy.getApplyMethod()
        );
    }
}
