package com.team10.backend.domain.transfer.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TransferFailedEventListener {

    private final TransferFailureRecorder transferFailureRecorder;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handle(TransferFailedEvent event) {
        transferFailureRecorder.recordFailed(
                event.senderAccountId(),
                event.receiverAccountId(),
                event.amount(),
                event.memo()
        );
    }
}
