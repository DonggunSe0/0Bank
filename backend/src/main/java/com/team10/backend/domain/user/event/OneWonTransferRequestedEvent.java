package com.team10.backend.domain.user.event;

/** 1원 송금 비동기 처리를 위해 트랜잭션 커밋 후 발행하는 이벤트. */
public record OneWonTransferRequestedEvent(
        Long verificationId,
        Long userId,
        String organization,
        String accountNumber
) {
}
