package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.global.idempotency.annotation.Idempotent;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferBusinessService transferBusinessService;

    // 오케스트레이터 패턴 -> @Transactional 제거
    @Idempotent(
            operationType = IdempotencyOperationType.TOPUP,
            userId = "#userId",
            key = "#idempotencyKey",
            hashFields = {"#accountId", "#amount", "#memo"}
    )
    public TopUpRes topUp(Long userId, String idempotencyKey, Long accountId, Long amount, String memo) {
        return transferBusinessService.executeTopUp(userId, accountId, amount, memo);
    }

    // 오케스트레이터 패턴 -> @Transactional 제거
    @Idempotent(
            operationType = IdempotencyOperationType.TRANSFER,
            userId = "#userId",
            key = "#idempotencyKey",
            hashFields = {"#senderAccountId", "#receiverAccountNumber", "#amount", "#memo"}
    )
    public TransferRes transfer(Long userId, String idempotencyKey, Long senderAccountId, String receiverAccountNumber, Long amount, String memo) {
        return transferBusinessService.executeTransfer(userId,
                senderAccountId,
                receiverAccountNumber,
                amount,
                memo);
    }

}
