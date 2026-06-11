package com.team10.backend.domain.user.dto.res;

import com.team10.backend.domain.user.type.VerificationStatus;

/**
 * OCR 접수 완료 응답.
 * 메인 스레드가 즉시 반환하는 202 Accepted 페이로드.
 *
 * @param verificationId 인증 세션 ID (이후 단계에서 사용)
 * @param status         현재 상태 (OCR_PENDING)
 * @param message        사용자 안내 메시지
 */
public record OcrAcceptedRes(
        Long verificationId,
        VerificationStatus status,
        String message
) {
}
