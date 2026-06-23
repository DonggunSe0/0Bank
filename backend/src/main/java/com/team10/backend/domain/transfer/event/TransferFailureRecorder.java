package com.team10.backend.domain.transfer.event;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.transfer.entity.Transfer;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferFailureRecorder {

    private final TransferRepository transferRepository;
    private final EntityManager entityManager;

    // 항상 새 트랜잭션을 시작
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(Long senderAccountId, Long receiverAccountId, Long amount, String memo) {
        Account senderAccount = entityManager.getReference(Account.class, senderAccountId);
        Account receiverAccount = entityManager.getReference(Account.class, receiverAccountId);

        Transfer failedTransfer = Transfer.failed(
                senderAccount,
                receiverAccount,
                amount,
                memo
        );

        transferRepository.save(failedTransfer);
    }
}
