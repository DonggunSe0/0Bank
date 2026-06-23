package com.team10.backend.domain.transfer.event;

public record TransferFailedEvent(
        Long senderAccountId,
        Long receiverAccountId,
        Long amount,
        String memo
) {
}
