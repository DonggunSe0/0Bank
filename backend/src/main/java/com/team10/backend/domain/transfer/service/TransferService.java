package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferBusinessService transferBusinessService;

    // 오케스트레이터 패턴 -> @Transactional 제거
    public TopUpRes topUp(Long userId, Long accountId, Long amount, String memo) {
        return transferBusinessService.executeTopUp(userId, accountId, amount, memo);
    }

    // 오케스트레이터 패턴 -> @Transactional 제거
    public TransferRes transfer(
            Long userId,
            Long senderAccountId,
            String receiverAccountNumber,
            String accountPassword,
            Long amount,
            String memo) {
        return transferBusinessService.executeTransfer(userId,
                senderAccountId,
                receiverAccountNumber,
                accountPassword,
                amount,
                memo);
    }

}
