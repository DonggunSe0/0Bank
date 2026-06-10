package com.team10.backend.domain.user.ocr;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import com.team10.backend.domain.user.verification.GovernmentVerifyResult;
import com.team10.backend.domain.user.verification.GovernmentVerifyTimeoutException;
import com.team10.backend.domain.user.verification.MockGovernmentVerifyService;
import com.team10.backend.domain.user.verification.VerificationSessionRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
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
 *      ├─ IDENTITY_NOT_FOUND  → FAILED
 *      └─ Timeout             → REQUIRES_NEW로 FAILED 커밋 후 메인 트랜잭션 ROLLBACK
 * </pre>
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

    @Async("ocrExecutor")
    @Transactional
    public void processAsync(byte[] imageBytes, Long verificationId) {
        log.info("[OCR] 1단계 시작 — verificationId={}, thread={}", verificationId, Thread.currentThread().getName());

        IdentityVerification verification = identityVerificationRepository.findById(verificationId)
                .orElseGet(() -> {
                    log.error("[OCR] 인증 세션을 찾을 수 없음 — verificationId={}", verificationId);
                    return null;
                });

        if (verification == null) return;

        try {
            // ── 1단계: Google Vision OCR ─────────────────────────────────────
            String rawText = visionImageClient.extractText(imageBytes);
            log.debug("[OCR] 추출 원문 — verificationId={}\n{}", verificationId, rawText);

            Optional<IdCardOcrResult> parsed = idCardParser.parse(rawText);

            if (parsed.isEmpty()) {
                verification.fail("OCR 파싱 실패: 필수 정보(이름·주민번호·발급일자)를 추출할 수 없습니다.");
                log.warn("[OCR] 파싱 실패 — verificationId={}", verificationId);
                return;
            }

            IdCardOcrResult result = parsed.get();
            verification.completeOcr(result.name(), result.residentNumber(), result.issueDate());
            log.info("[OCR] 1단계 완료 — verificationId={}, name={}", verificationId, result.name());

            // ── 2단계: 행안부 진위 확인 즉시 체이닝 ────────────────────────────
            chainGovernmentVerification(verification, result);

        } catch (IOException e) {
            verification.fail("이미지 처리 오류: " + e.getMessage());
            log.error("[OCR] 이미지 오류 — verificationId={}", verificationId, e);
        }
    }

    private void chainGovernmentVerification(IdentityVerification verification, IdCardOcrResult result) {
        log.info("[GOV] 2단계 시작 — verificationId={}", verification.getId());

        try {
            GovernmentVerifyResult govResult = mockGovernmentVerifyService.verify(
                    result.name(), result.residentNumber(), result.issueDate()
            );

            switch (govResult) {
                case VERIFIED -> {
                    verification.completeGovernmentVerification();
                    log.info("[GOV] 2단계 완료 — verificationId={}, 다음 단계: 1원 송금 대기", verification.getId());
                }
                case ISSUE_DATE_MISMATCH -> {
                    verification.fail("분실·도난 신분증 의심: 발급일자가 정부 기록과 일치하지 않습니다.");
                    log.warn("[GOV] 발급일자 불일치 — verificationId={}", verification.getId());
                }
                case IDENTITY_NOT_FOUND -> {
                    verification.fail("존재하지 않는 명의: 위조 신분증이 의심됩니다.");
                    log.warn("[GOV] 존재하지 않는 명의 — verificationId={}", verification.getId());
                }
            }

        } catch (GovernmentVerifyTimeoutException e) {
            log.error("[GOV] 타임아웃 — verificationId={}, 메인 트랜잭션 롤백 예정", verification.getId(), e);
            verificationSessionRecorder.markFailedInNewTransaction(
                    verification.getId(),
                    "행안부 연동 타임아웃: 잠시 후 다시 시도해주세요."
            );
            throw e;
        }
    }
}
