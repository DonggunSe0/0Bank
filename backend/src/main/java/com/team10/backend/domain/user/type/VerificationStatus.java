package com.team10.backend.domain.user.type;

/** 본인인증 단계별 상태. */
public enum VerificationStatus {
    OCR_PENDING,
    OCR_COMPLETED,
    GOVERNMENT_VERIFIED,
    ONE_WON_IN_PROGRESS,
    ONE_WON_PENDING,
    COMPLETED,
    FAILED
}
