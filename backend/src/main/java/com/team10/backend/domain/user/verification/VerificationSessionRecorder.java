package com.team10.backend.domain.user.verification;

import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 타임아웃 FAILED 상태를 REQUIRES_NEW 트랜잭션으로 독립 커밋하는 헬퍼.
 * OCR saveOcrSuccess가 이미 커밋된 후 행안부 타임아웃이 발생해도 FAILED를 안전하게 기록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationSessionRecorder {

    private final IdentityVerificationRepository identityVerificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedInNewTransaction(Long verificationId, String reason) {
        identityVerificationRepository.findById(verificationId).ifPresent(verification -> {
            verification.fail(reason);
            log.info("[GOV] 타임아웃 FAILED 상태 별도 커밋 — verificationId={}", verificationId);
        });
    }
}
