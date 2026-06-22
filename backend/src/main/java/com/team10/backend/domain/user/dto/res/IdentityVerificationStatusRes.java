package com.team10.backend.domain.user.dto.res;

import com.team10.backend.domain.user.type.VerificationStatus;

/**
 * 본인인증 진행 상태 조회 응답.
 * OCR/1원송금이 비동기로 처리되므로, 클라이언트는 이 API로 폴링하여 진행 상태를 확인한다.
 *
 * @param verificationId 본인인증 세션 ID
 * @param status         현재 상태
 * @param failureReason  실패(또는 재시도 가능한 복구) 사유 — 해당 사유가 없으면 null
 */
public record IdentityVerificationStatusRes(
        Long verificationId,
        VerificationStatus status,
        String failureReason
) {
}
