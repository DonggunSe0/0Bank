package com.team10.backend.domain.youngPolicy.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "RAG 기반 청년정책 추천 응답")
public record YoungPolicyRecommendRes(
        @Schema(description = "추천 사유가 포함된 4가지 청년정책 카드 목록")
        List<YoungPolicyRecommendItem> recommendedPolicies
) {
}
