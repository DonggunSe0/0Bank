package com.team10.backend.domain.user.ocr;

import com.team10.backend.domain.codef.auth.ocr.IdCardOcrResult;
import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import com.team10.backend.global.crypto.HmacHasher;
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
    private final HmacHasher hmacHasher;

    /** user를 fetch join으로 함께 로딩한다 — 트랜잭션이 끝난 뒤에도 verification.getUser()를 안전하게 쓸 수 있게 한다. */
    @Transactional(readOnly = true)
    public IdentityVerification loadVerification(Long verificationId) {
        return identityVerificationRepository.findByIdWithUser(verificationId)
                .orElseGet(() -> {
                    log.error("[OCR] 인증 세션을 찾을 수 없음 — verificationId={}", verificationId);
                    return null;
                });
    }

    @Transactional
    public void saveOcrSuccess(Long verificationId, IdCardOcrResult result) {
        String residentNumberHash = hmacHasher.hash(result.residentNumber());
        identityVerificationRepository.findById(verificationId).ifPresent(v ->
                v.completeOcr(result.name(), result.residentNumber(), result.issueDate(), residentNumberHash)
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
