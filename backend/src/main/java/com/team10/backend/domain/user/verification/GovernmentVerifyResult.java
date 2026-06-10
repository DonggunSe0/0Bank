package com.team10.backend.domain.user.verification;

/**
 * 행안부(실제) / MockGovernmentVerifyService(Mock) 진위 확인 결과.
 *
 * <ul>
 *   <li>{@link #VERIFIED}             — 신분증 진위 확인 성공</li>
 *   <li>{@link #ISSUE_DATE_MISMATCH}  — 발급일자 불일치 (분실·도난 신분증 의심)</li>
 *   <li>{@link #IDENTITY_NOT_FOUND}   — 존재하지 않는 명의 (위조 신분증)</li>
 * </ul>
 *
 * 타임아웃은 결과 값이 아닌 {@link GovernmentVerifyTimeoutException} 예외로 표현한다.
 */
public enum GovernmentVerifyResult {
    VERIFIED,
    ISSUE_DATE_MISMATCH,
    IDENTITY_NOT_FOUND
}
