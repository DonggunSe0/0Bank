package com.team10.backend.domain.youngPolicy.dto.res;

import com.team10.backend.domain.youngPolicy.entity.YoungPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천된 개별 청년정책 카드 정보")
public record YoungPolicyRecommendItem(
        Long id,
        String policyId,
        String title,
        String category,
        String subCategory,
        Integer minAge,
        Integer maxAge,
        String regionCode,
        String applyPeriod,
        
        @Schema(description = "이 정책이 사용자에게 추천된 맞춤형 사유", example = "임차료 지원 혜택이 있어 초기 창업 비용을 크게 줄일 수 있습니다.")
        String recommendReason
) {
    public static YoungPolicyRecommendItem of(YoungPolicy policy, String recommendReason) {
        return new YoungPolicyRecommendItem(
                policy.getId(),
                policy.getPolicyId(),
                policy.getTitle(),
                policy.getCategory(),
                policy.getSubCategory(),
                policy.getMinAge(),
                policy.getMaxAge(),
                policy.getRegionCode(),
                policy.getApplyPeriod(),
                recommendReason
        );
    }
}
