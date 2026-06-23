package com.team10.backend.domain.user.dto.res;

import com.team10.backend.domain.user.type.VerificationStatus;

/** 본인인증 진행 상태 조회(폴링) 응답. */
public record IdentityVerificationStatusRes(
        Long verificationId,
        VerificationStatus status,
        String failureReason
) {
}
