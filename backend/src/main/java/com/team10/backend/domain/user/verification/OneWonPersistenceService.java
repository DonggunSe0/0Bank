package com.team10.backend.domain.user.verification;

import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1원 송금 비동기 처리 결과를 DB에 반영하는 전담 서비스.
 * {@link OneWonTransferProcessor}(비동기 메서드)에서 self-injection 없이 @Transactional을
 * 사용하기 위해 별도 클래스로 분리했다 — OcrService/OcrPersistenceService와 동일한 패턴.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OneWonPersistenceService {

    private final IdentityVerificationRepository identityVerificationRepository;

    /** 비동기 송금 성공 — ONE_WON_PENDING(코드 입력 대기)으로 전환 */
    @Transactional
    public void markSent(Long verificationId) {
        identityVerificationRepository.findById(verificationId).ifPresent(v -> {
            v.startOneWon();
            log.info("[1원 인증] 송금 완료 상태 반영 — verificationId={}", verificationId);
        });
    }

    /**
     * 비동기 송금 실패 — 재시도 가능한 GOVERNMENT_VERIFIED로 되돌리고 사유만 기록한다.
     * 호출 시점(비동기 스레드, AFTER_COMMIT 리스너)에 활성 트랜잭션이 없는 경우가 대부분이지만,
     * 어느 컨텍스트에서 호출되어도 독립적으로 커밋되도록 REQUIRES_NEW를 명시한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long verificationId, String reason) {
        identityVerificationRepository.findById(verificationId).ifPresent(v -> {
            v.revertOneWonRequest(reason);
            log.warn("[1원 인증] 비동기 송금 실패 — 재시도 가능 상태로 복구, verificationId={}", verificationId);
        });
    }
}
