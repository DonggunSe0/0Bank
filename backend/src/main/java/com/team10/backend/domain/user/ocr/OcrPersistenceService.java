package com.team10.backend.domain.user.ocr;

import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OCR 결과 DB 저장 전담 서비스 — self-injection 없이 @Transactional 사용하기 위해 OcrService에서 분리 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrPersistenceService {

    private final IdentityVerificationRepository identityVerificationRepository;

    @Transactional(readOnly = true)
    public IdentityVerification loadVerification(Long verificationId) {
        return identityVerificationRepository.findById(verificationId)
                .orElseGet(() -> {
                    log.error("[OCR] 인증 세션을 찾을 수 없음 — verificationId={}", verificationId);
                    return null;
                });
    }

    @Transactional
    public void saveOcrSuccess(Long verificationId, IdCardOcrResult result) {
        identityVerificationRepository.findById(verificationId).ifPresent(v ->
                v.completeOcr(result.name(), result.residentNumber(), result.issueDate())
        );
    }

    @Transactional
    public void saveGovSuccess(Long verificationId) {
        identityVerificationRepository.findById(verificationId).ifPresent(
                IdentityVerification::completeGovernmentVerification
        );
    }

    @Transactional
    public void saveFailure(Long verificationId, String reason) {
        identityVerificationRepository.findById(verificationId).ifPresent(v -> v.fail(reason));
    }
}
