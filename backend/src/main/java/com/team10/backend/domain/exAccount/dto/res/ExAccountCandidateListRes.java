package com.team10.backend.domain.exAccount.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "외부 계좌 연동 후보 목록 응답")
public record ExAccountCandidateListRes(
        @Schema(description = "일회용 임시 연동 토큰", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        String candidateToken,
        @Schema(description = "토큰 만료 시간 (초)", example = "300")
        long expiresInSeconds,
        @Schema(description = "계좌 후보 목록")
        List<ExAccountCandidateRes> accounts
) {
}
