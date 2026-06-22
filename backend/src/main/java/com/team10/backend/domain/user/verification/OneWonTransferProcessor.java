package com.team10.backend.domain.user.verification;

import com.team10.backend.domain.user.event.OneWonTransferRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 1원 송금 비동기 처리 서비스.
 * 은행 API 응답을 건당 최대 30초까지 기다려야 하므로(CodefBankRestClientConfig readTimeout=30s),
 * 요청 스레드(Tomcat worker)를 점유하지 않도록 별도 스레드풀(oneWonExecutor)에서 실행한다.
 * OcrService와 동일한 "accept 동기 / 처리 비동기" 패턴.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OneWonTransferProcessor {

    private final BankTransferService bankTransferService;
    private final OneWonVerificationService oneWonVerificationService;
    private final OneWonPersistenceService oneWonPersistenceService;

    @Async("oneWonExecutor")
    public void processAsync(OneWonTransferRequestedEvent event) {
        Long verificationId = event.verificationId();
        Long userId = event.userId();
        log.info("[1원 인증] 비동기 송금 시작 — verificationId={}, thread={}",
                verificationId, Thread.currentThread().getName());

        try {
            String code = oneWonVerificationService.generateAndStore(verificationId, userId);
            try {
                bankTransferService.sendOneWon(event.organization(), event.accountNumber(), code);
                oneWonPersistenceService.markSent(verificationId);
                log.info("[1원 인증] 비동기 송금 완료 — verificationId={}", verificationId);
            } catch (Exception e) {
                // 송금 실패 — Redis 코드/일일 카운터 보상 롤백 후, 재시도 가능 상태로 복구
                oneWonVerificationService.deleteCode(verificationId);
                oneWonVerificationService.decrementDailyCount(userId);
                oneWonPersistenceService.markFailed(verificationId, "1원 송금에 실패했습니다. 다시 시도해주세요.");
                log.error("[1원 인증] 비동기 송금 실패 — verificationId={}", verificationId, e);
            }
        } finally {
            // 동시 중복 요청 방지 락 — 송금 시도(성공/실패 불문) 완료 후 해제
            oneWonVerificationService.releaseStartLock(userId);
        }
    }
}
