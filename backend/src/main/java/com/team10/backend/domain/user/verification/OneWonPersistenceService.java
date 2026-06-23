package com.team10.backend.domain.user.verification;

import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 1원 송금 비동기 처리 결과를 DB에 반영하는 전담 서비스. */
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

    /** 비동기 송금 실패 — GOVERNMENT_VERIFIED로 복구. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long verificationId, String reason) {
        identityVerificationRepository.findById(verificationId).ifPresent(v -> {
            v.revertOneWonRequest(reason);
            log.warn("[1원 인증] 비동기 송금 실패 — 재시도 가능 상태로 복구, verificationId={}", verificationId);
        });
    }
}
