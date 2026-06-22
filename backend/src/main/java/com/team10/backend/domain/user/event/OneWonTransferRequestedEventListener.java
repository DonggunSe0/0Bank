package com.team10.backend.domain.user.event;

import com.team10.backend.domain.user.verification.OneWonPersistenceService;
import com.team10.backend.domain.user.verification.OneWonTransferProcessor;
import com.team10.backend.domain.user.verification.OneWonVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.RejectedExecutionException;

/**
 * 1원 송금 요청 트랜잭션이 커밋된 후 실제 비동기 송금 처리를 시작한다.
 * OcrSubmittedEventListener와 동일한 패턴 — 리스너 자체는 동기지만 호출하는 processAsync가
 * @Async이므로 즉시 반환된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OneWonTransferRequestedEventListener {

    private final OneWonTransferProcessor oneWonTransferProcessor;
    private final OneWonVerificationService oneWonVerificationService;
    private final OneWonPersistenceService oneWonPersistenceService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OneWonTransferRequestedEvent event) {
        try {
            oneWonTransferProcessor.processAsync(event);
        } catch (RejectedExecutionException e) {
            // 스레드풀+큐 포화로 작업 자체가 시작되지 못함 — ONE_WON_IN_PROGRESS에 멈추지 않도록
            // 재시도 가능한 GOVERNMENT_VERIFIED로 되돌리고 사유만 기록한다.
            log.error("[1원 인증] 스레드풀 포화로 작업 거부 — verificationId={}", event.verificationId(), e);
            oneWonPersistenceService.markFailed(
                    event.verificationId(),
                    "서버 처리량이 많아 요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."
            );
            // processAsync가 시작되지 못해 자체적으로 락을 해제하지 못하므로 여기서 직접 해제
            oneWonVerificationService.releaseStartLock(event.userId());
        }
    }
}
