package com.team10.backend.domain.transfer.dto.res;

import com.team10.backend.domain.transfer.entity.Transfer;
import com.team10.backend.domain.transfer.type.TransferStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "계좌 간 송금 응답")
public record TransferRes(
        @Schema(description = "송금 ID", example = "20")
        Long transferId,

        @Schema(description = "송금 상태", example = "SUCCESS")
        TransferStatus status,

        @Schema(description = "출금 계좌 ID", example = "1")
        Long senderAccountId,

        @Schema(description = "출금 계좌번호", example = "100200300001")
        String senderAccountNumber,

        @Schema(description = "입금 계좌번호", example = "100200300002")
        String receiverAccountNumber,

        @Schema(description = "송금 금액", example = "50000")
        Long amount,

        @Schema(description = "출금 계좌 송금 후 잔액", example = "50000")
        Long senderBalanceAfter,

        @Schema(description = "송금 메모", example = "점심값")
        String memo,

        @Schema(description = "송금 처리 시각", example = "2026-06-17T10:10:00")
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
