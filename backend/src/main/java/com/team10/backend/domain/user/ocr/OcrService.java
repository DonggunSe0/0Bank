package com.team10.backend.domain.user.ocr;

import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import com.team10.backend.domain.user.verification.GovernmentVerifyResult;
import com.team10.backend.domain.user.verification.GovernmentVerifyTimeoutException;
import com.team10.backend.domain.user.verification.MockGovernmentVerifyService;
import com.team10.backend.domain.user.verification.VerificationSessionRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Google Cloud Vision API 기반 OCR 비동기 처리 서비스 (1단계 → 2단계 즉시 체이닝).
 *
 * <h2>처리 흐름</h2>
 * <pre>
 * [ocrExecutor 스레드]
 *   1. 이미지 바이트 → Google Cloud Vision API (DOCUMENT_TEXT_DETECTION)
 *   2. 추출된 전체 텍스트 → Regex 파싱 [이름, 주민번호, 발급일자]
 *      └─ 파싱 실패 → FAILED 저장, 종료
 *   3. [즉시 체이닝] MockGovernmentVerifyService.verify()
 *      ├─ VERIFIED            → GOVERNMENT_VERIFIED (3단계 대기)
 *      ├─ ISSUE_DATE_MISMATCH → FAILED
 *      └─ IDENTITY_NOT_FOUND  → FAILED
 * </pre>
 *
 * <p>Vision API 호출(느린 외부 I/O) 동안 DB 커넥션을 점유하지 않도록
 * {@code @Transactional} 을 메서드 전체가 아닌 DB 저장 시점에만 적용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final IdCardParser idCardParser;
    private final VisionImageClient visionImageClient;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final MockGovernmentVerifyService mockGovernmentVerifyService;
    private final VerificationSessionRecorder verificationSessionRecorder;

    /** Self-injection: @Transactional 메서드를 같은 빈 내에서 호출할 때 프록시를 통하도록 한다. */
    @Autowired
    @Lazy
    private OcrService self;

    /**
     * 비동기 OCR 처리 진입점.
     * Vision API 호출은 트랜잭션 밖에서 수행하고, DB 저장만 별도 트랜잭션으로 위임한다.
     */
    @Async("ocrExecutor")
    public void processAsync(byte[] imageBytes, Long verificationId) {
        log.info("[OCR] 1단계 시작 — verificationId={}, thread={}", verificationId, Thread.currentThread().getName());

        if (self.loadVerification(verificationId) == null) return;

        try {
            // ── 1단계: Google Vision OCR (트랜잭션 밖) ──────────────────────
            String rawText = visionImageClient.extractText(imageBytes);

            Optional<IdCardOcrResult> parsed = idCardParser.parse(rawText);

            if (parsed.isEmpty()) {
                self.saveFailure(verificationId, "OCR 파싱 실패: 필수 정보(이름·주민번호·발급일자)를 추출할 수 없습니다.");
                log.warn("[OCR] 파싱 실패 — verificationId={}", verificationId);
                return;
            }

            IdCardOcrResult result = parsed.get();
            self.saveOcrSuccess(verificationId, result);
            log.info("[OCR] 1단계 완료 — verificationId={}, name={}", verificationId, result.name());

            // ── 2단계: 행안부 진위 확인 즉시 체이닝 ────────────────────────
            chainGovernmentVerification(verificationId, result);

        } catch (Exception e) {
            self.saveFailure(verificationId, "이미지 처리 중 오류가 발생했습니다: " + e.getMessage());
            log.error("[OCR] 처리 오류 — verificationId={}", verificationId, e);
        }
    }

    // ── DB 접근 메서드 (각각 짧은 트랜잭션) ────────────────────────────────

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

    // ── 2단계 체이닝 ────────────────────────────────────────────────────────

    private void chainGovernmentVerification(Long verificationId, IdCardOcrResult result) {
        log.info("[GOV] 2단계 시작 — verificationId={}", verificationId);

        try {
            GovernmentVerifyResult govResult = mockGovernmentVerifyService.verify(
                    result.name(), result.residentNumber(), result.issueDate()
            );

            switch (govResult) {
                case VERIFIED -> {
                    self.saveGovSuccess(verificationId);
                    log.info("[GOV] 2단계 완료 — verificationId={}, 다음 단계: 1원 송금 대기", verificationId);
                }
                case ISSUE_DATE_MISMATCH -> {
                    self.saveFailure(verificationId, "분실·도난 신분증 의심: 발급일자가 정부 기록과 일치하지 않습니다.");
                    log.warn("[GOV] 발급일자 불일치 — verificationId={}", verificationId);
                }
                case IDENTITY_NOT_FOUND -> {
                    self.saveFailure(verificationId, "존재하지 않는 명의: 위조 신분증이 의심됩니다.");
                    log.warn("[GOV] 존재하지 않는 명의 — verificationId={}", verificationId);
                }
            }

        } catch (GovernmentVerifyTimeoutException e) {
            log.error("[GOV] 타임아웃 — verificationId={}", verificationId, e);
            verificationSessionRecorder.markFailedInNewTransaction(
                    verificationId,
                    "행안부 연동 타임아웃: 잠시 후 다시 시도해주세요."
            );
        }
    }
}
