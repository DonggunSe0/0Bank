package com.team10.backend.domain.user.event;

/**
 * 1원 송금 요청이 접수되어 트랜잭션 커밋 후 비동기로 실제 송금을 처리하기 위해 발행하는 이벤트.
 *
 * @param verificationId 본인인증 세션 ID
 * @param userId         요청한 사용자 ID
 * @param organization   은행/기관 코드
 * @param accountNumber  송금 대상 계좌번호
 */
public record OneWonTransferRequestedEvent(
        Long verificationId,
        Long userId,
        String organization,
        String accountNumber
) {
}
