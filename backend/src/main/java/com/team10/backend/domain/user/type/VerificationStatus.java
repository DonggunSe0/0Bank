package com.team10.backend.domain.user.type;

/**
 * 본인인증 단계별 상태
 *
 * OCR_PENDING        → 이미지 접수, OCR 처리 중
 * OCR_COMPLETED      → OCR 파싱 완료, 행안부 검증 대기
 * GOVERNMENT_VERIFIED → 행안부 진위 확인 완료, 1원 송금 대기
 * ONE_WON_PENDING    → 1원 송금 완료, 사용자 입력 대기
 * COMPLETED          → 본인인증 최종 완료
 * FAILED             → 인증 실패 (위조, 타임아웃, 브루트포스 등)
 */
public enum VerificationStatus {
    OCR_PENDING,
    OCR_COMPLETED,
    GOVERNMENT_VERIFIED,
    ONE_WON_PENDING,
    COMPLETED,
    FAILED
}
