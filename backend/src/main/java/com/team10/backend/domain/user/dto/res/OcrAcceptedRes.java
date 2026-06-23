package com.team10.backend.domain.user.dto.res;

import com.team10.backend.domain.user.type.VerificationStatus;

/** OCR 접수 완료 응답(202 Accepted 페이로드). */
public record OcrAcceptedRes(
        Long verificationId,
        VerificationStatus status,
        String message
) {
}
