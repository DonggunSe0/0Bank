package com.team10.backend.domain.user.verification;

import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 세션 상태를 독립 트랜잭션으로 기록하는 헬퍼.
 *
 * <h2>존재 이유</h2>
 * <p>{@code OcrService.processAsync()} 는 {@code @Transactional} 경계 내에서 실행된다.
 * 행안부 타임아웃이 발생하면 {@link GovernmentVerifyTimeoutException} 이 던져지고
 * Spring 이 해당 트랜잭션 전체를 롤백한다.
 * 이 경우 OCR 파싱 결과 업데이트({@code completeOcr})도 함께 롤백되어
 * 데이터 정합성이 보장된다.
 *
 * <p>하지만 사용자에게 "타임아웃으로 실패했다"는 사실은 알려야 하므로,
 * 롤백된 메인 트랜잭션과 무관하게 {@code REQUIRES_NEW} 로 새 트랜잭션을 열어
 * {@code FAILED} 상태를 별도 커밋한다.
 *
 * <pre>
 * [ocrExecutor 스레드]
 *   ┌─ 메인 트랜잭션 (@Transactional) ────────────────────────────────┐
 *   │  completeOcr() ─ DB write                                       │
 *   │  verify()      ─ GovernmentVerifyTimeoutException 발생          │
 *   │                   ↓                                             │
 *   │          markFailedInNewTransaction() ◄─ REQUIRES_NEW 트랜잭션  │
 *   │             FAILED 상태 즉시 커밋 (독립)                         │
 *   │                   ↓                                             │
 *   │          예외 재전파 → 메인 트랜잭션 ROLLBACK                    │
 *   │          (completeOcr write 취소됨)                             │
 *   └─────────────────────────────────────────────────────────────────┘
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationSessionRecorder {

    private final IdentityVerificationRepository identityVerificationRepository;

    /**
     * 메인 트랜잭션이 롤백되더라도 FAILED 상태를 보존한다.
     *
     * <p>{@link Propagation#REQUIRES_NEW} — 호출 시 새 트랜잭션을 시작하고,
     * 메인 트랜잭션은 일시 중단된다. 이 메서드가 커밋된 후 메인 트랜잭션이 롤백된다.
     *
     * @param verificationId 실패 처리할 인증 세션 ID
     * @param reason         실패 사유
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedInNewTransaction(Long verificationId, String reason) {
        identityVerificationRepository.findById(verificationId).ifPresent(verification -> {
            verification.fail(reason);
            log.info("[GOV] 타임아웃 FAILED 상태 별도 커밋 — verificationId={}", verificationId);
        });
    }
}
