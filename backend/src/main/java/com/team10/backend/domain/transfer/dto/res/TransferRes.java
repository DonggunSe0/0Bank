package com.team10.backend.domain.transfer.dto.res;

import com.team10.backend.domain.transfer.entity.Transfer;
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
    public static TransferRes from(
            Transfer transfer,
            LocalDateTime transferredAt
    ) {
        return new TransferRes(
                transfer.getId(),
                transfer.getStatus(),
                transfer.getSenderAccount().getId(),
                transfer.getSenderAccount().getAccountNumber(),
                transfer.getReceiverAccount().getAccountNumber(),
                transfer.getAmount(),
                transfer.getSenderAccount().getBalance(),
                transfer.getMemo(),
                transferredAt
        );
    }
}
