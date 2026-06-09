package com.team10.backend.domain.transfer.dto.res;

import com.team10.backend.domain.transfer.type.TransferStatus;
import java.time.LocalDateTime;

public record TransferRes(
        Long transferId,
        TransferStatus status,
        Long senderAccountId,
        String senderAccountNumber,
        String receiverAccountNumber,
        Long amount,
        Long senderBalanceAfter,
        String memo,
        LocalDateTime transferredAt
) {
}
