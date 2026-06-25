package com.team10.backend.domain.user.ocr;

import com.team10.backend.domain.codef.auth.ocr.CodefOcrClient;
import com.team10.backend.domain.codef.auth.ocr.IdCardOcrResult;
import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.verification.GovernmentVerifyResult;
import com.team10.backend.domain.user.verification.GovernmentVerifyTimeoutException;
import com.team10.backend.domain.user.verification.MockGovernmentVerifyService;
import com.team10.backend.domain.user.verification.VerificationSessionRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * 신분증 OCR 비동기 처리 서비스 (1단계 → 2단계 즉시 체이닝).
 * 외부 API 대기 중 DB 커넥션 점유를 피하기 위해 DB 저장은 {@link OcrPersistenceService}에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final CodefOcrClient codefOcrClient;
    private final OcrPersistenceService ocrPersistenceService;
    private final MockGovernmentVerifyService mockGovernmentVerifyService;
    private final VerificationSessionRecorder verificationSessionRecorder;

    @Async("ocrExecutor")
    public void processAsync(Path imagePath, Long verificationId) {
        log.info("[OCR] 1단계 시작 — verificationId={}, thread={}", verificationId, Thread.currentThread().getName());

        try {
            IdentityVerification verification = ocrPersistenceService.loadVerification(verificationId);
            if (verification == null) return;

            try {
                byte[] imageBytes = Files.readAllBytes(imagePath);
                IdCardOcrResult result = codefOcrClient.extractIdCard(imageBytes);
                ocrPersistenceService.saveOcrSuccess(verificationId, result);
                log.info("[OCR] 1단계 완료 — verificationId={}", verificationId); // 이름(PII)은 로그에 남기지 않음

                if (!matchesAccountHolder(verification.getUser(), result)) {
                    ocrPersistenceService.saveFailure(verificationId,
                            "본인 명의의 신분증이 아닙니다. 가입 시 등록한 정보와 일치하는 신분증으로 다시 시도해주세요.");
                    log.warn("[OCR] 본인 명의 불일치 — verificationId={}", verificationId); // 이름(PII)은 로그에 남기지 않음
                    return;
                }

                chainGovernmentVerification(verificationId, result);

            } catch (Exception e) {
                // DB failureReason에는 고정 메시지만 저장 — 예외 메시지(e.getMessage())는 내부 정보를
                // 노출할 수 있어 평문 저장하지 않는다. 상세 원인은 아래 log.error의 스택트레이스로만 남긴다.
                ocrPersistenceService.saveFailure(verificationId, "이미지 처리 중 오류가 발생했습니다.");
                log.error("[OCR] 처리 오류 — verificationId={}", verificationId, e);
            }
        } finally {
            // 앱이 직접 만든 임시파일이므로 처리 성공/실패와 무관하게 항상 정리한다.
            try {
                Files.deleteIfExists(imagePath);
            } catch (IOException e) {
                log.warn("[OCR] 임시파일 삭제 실패 — path={}", imagePath, e);
            }
        }
    }

    private void chainGovernmentVerification(Long verificationId, IdCardOcrResult result) {
        log.info("[GOV] 2단계 시작 — verificationId={}", verificationId);

        try {
            GovernmentVerifyResult govResult = mockGovernmentVerifyService.verify(
                    result.name(), result.residentNumber(), result.issueDate()
            );

            switch (govResult) {
                case VERIFIED -> {
                    ocrPersistenceService.saveGovSuccess(verificationId);
                    log.info("[GOV] 2단계 완료 — verificationId={}", verificationId);
                }
                case ISSUE_DATE_MISMATCH -> {
                    ocrPersistenceService.saveFailure(verificationId, "분실·도난 신분증 의심: 발급일자가 정부 기록과 일치하지 않습니다.");
                    log.warn("[GOV] 발급일자 불일치 — verificationId={}", verificationId);
                }
                case IDENTITY_NOT_FOUND -> {
                    ocrPersistenceService.saveFailure(verificationId, "존재하지 않는 명의: 위조 신분증이 의심됩니다.");
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

    /**
     * OCR로 읽은 이름·생년월일이 인증을 요청한 계정 본인 정보와 일치하는지 확인한다.
     * 행안부 진위 확인({@link MockGovernmentVerifyService})은 "신분증 자체가 진짜인지"만 보고
     * "그 신분증이 이 계정의 주인 것인지"는 보지 않으므로, 타인(실재하는 진짜 신분증) 명의 도용을
     * 막으려면 이 비교가 별도로 필요하다.
     */
    private boolean matchesAccountHolder(User user, IdCardOcrResult result) {
        if (!user.getName().trim().equals(result.name().trim())) {
            return false;
        }
        LocalDate ocrBirthDate = parseBirthDate(result.residentNumber());
        return ocrBirthDate != null && ocrBirthDate.equals(user.getBirthDate());
    }

    /**
     * 주민등록번호 "YYMMDD-S......" 형식에서 생년월일을 복원한다.
     * 7번째 자리(성별/세기 구분 숫자) 기준: 1·2(1900년대), 3·4(2000년대), 5·6(1900년대 외국인),
     * 7·8(2000년대 외국인). 형식이 예상과 다르면 null을 반환해 이름만으로 판단하지 않고 불일치로 처리한다.
     */
    private LocalDate parseBirthDate(String residentNumber) {
        if (residentNumber == null || residentNumber.length() < 8 || residentNumber.charAt(6) != '-') {
            return null;
        }
        try {
            int yy = Integer.parseInt(residentNumber.substring(0, 2));
            int mm = Integer.parseInt(residentNumber.substring(2, 4));
            int dd = Integer.parseInt(residentNumber.substring(4, 6));
            int century = switch (residentNumber.charAt(7)) {
                case '1', '2', '5', '6' -> 1900;
                case '3', '4', '7', '8' -> 2000;
                default -> -1;
            };
            if (century == -1) return null;
            return LocalDate.of(century + yy, mm, dd);
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }
}
