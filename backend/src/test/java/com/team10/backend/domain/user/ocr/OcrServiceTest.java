package com.team10.backend.domain.user.ocr;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.team10.backend.domain.codef.auth.ocr.CodefOcrClient;
import com.team10.backend.domain.codef.auth.ocr.IdCardOcrResult;
import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.verification.GovernmentVerifyResult;
import com.team10.backend.domain.user.verification.GovernmentVerifyTimeoutException;
import com.team10.backend.domain.user.verification.MockGovernmentVerifyService;
import com.team10.backend.domain.user.verification.VerificationSessionRecorder;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock CodefOcrClient codefOcrClient;
    @Mock OcrPersistenceService ocrPersistenceService;
    @Mock MockGovernmentVerifyService mockGovernmentVerifyService;
    @Mock VerificationSessionRecorder verificationSessionRecorder;

    @InjectMocks
    OcrService ocrService;

    @Nested
    @DisplayName("processAsync")
    class ProcessAsync {

        private Path imagePath;

        @BeforeEach
        void setUp() throws Exception {
            imagePath = Files.createTempFile("ocr-test-", ".tmp");
            Files.write(imagePath, new byte[]{1});
        }

        @AfterEach
        void tearDown() throws Exception {
            Files.deleteIfExists(imagePath);
        }

        @Test
        @DisplayName("인증 세션을 찾을 수 없으면 아무 처리도 하지 않는다")
        void verificationNotFound_doesNothing() {
            when(ocrPersistenceService.loadVerification(10L)).thenReturn(null);

            ocrService.processAsync(imagePath, 10L);

            verifyNoInteractions(codefOcrClient);
            verify(ocrPersistenceService, never()).saveOcrSuccess(any(), any());
            verify(ocrPersistenceService, never()).saveFailure(any(), any());
        }

        @Test
        @DisplayName("OCR 성공 + 행안부 인증 성공 → saveGovSuccess 호출")
        void ocrSuccess_govVerified() {
            when(ocrPersistenceService.loadVerification(10L)).thenReturn(mock(IdentityVerification.class));
            IdCardOcrResult result = new IdCardOcrResult("홍길동", "901201-1234567", "2023-01-15");
            when(codefOcrClient.extractIdCard(any())).thenReturn(result);
            when(mockGovernmentVerifyService.verify("홍길동", "901201-1234567", "2023-01-15"))
                    .thenReturn(GovernmentVerifyResult.VERIFIED);

            ocrService.processAsync(imagePath, 10L);

            verify(ocrPersistenceService).saveOcrSuccess(10L, result);
            verify(ocrPersistenceService).saveGovSuccess(10L);
            verify(ocrPersistenceService, never()).saveFailure(any(), any());
        }

        @Test
        @DisplayName("행안부 발급일자 불일치 → saveFailure(분실·도난 의심)")
        void govIssueDateMismatch_savesFailure() {
            when(ocrPersistenceService.loadVerification(10L)).thenReturn(mock(IdentityVerification.class));
            IdCardOcrResult result = new IdCardOcrResult("홍길동", "901201-1234567", "2023-01-15");
            when(codefOcrClient.extractIdCard(any())).thenReturn(result);
            when(mockGovernmentVerifyService.verify(any(), any(), any()))
                    .thenReturn(GovernmentVerifyResult.ISSUE_DATE_MISMATCH);

            ocrService.processAsync(imagePath, 10L);

            verify(ocrPersistenceService).saveFailure(eq(10L), contains("분실"));
            verify(ocrPersistenceService, never()).saveGovSuccess(any());
        }

        @Test
        @DisplayName("행안부 존재하지 않는 명의 → saveFailure(위조 의심)")
        void govIdentityNotFound_savesFailure() {
            when(ocrPersistenceService.loadVerification(10L)).thenReturn(mock(IdentityVerification.class));
            IdCardOcrResult result = new IdCardOcrResult("홍길동", "901201-1234567", "2023-01-15");
            when(codefOcrClient.extractIdCard(any())).thenReturn(result);
            when(mockGovernmentVerifyService.verify(any(), any(), any()))
                    .thenReturn(GovernmentVerifyResult.IDENTITY_NOT_FOUND);

            ocrService.processAsync(imagePath, 10L);

            verify(ocrPersistenceService).saveFailure(eq(10L), contains("위조"));
            verify(ocrPersistenceService, never()).saveGovSuccess(any());
        }

        @Test
        @DisplayName("행안부 타임아웃 → 별도 트랜잭션에 FAILED 기록, saveFailure는 호출되지 않음")
        void govTimeout_recordsInNewTransaction() {
            when(ocrPersistenceService.loadVerification(10L)).thenReturn(mock(IdentityVerification.class));
            IdCardOcrResult result = new IdCardOcrResult("홍길동", "901201-1234567", "2023-01-15");
            when(codefOcrClient.extractIdCard(any())).thenReturn(result);
            when(mockGovernmentVerifyService.verify(any(), any(), any()))
                    .thenThrow(new GovernmentVerifyTimeoutException("타임아웃"));

            ocrService.processAsync(imagePath, 10L);

            verify(verificationSessionRecorder).markFailedInNewTransaction(eq(10L), contains("타임아웃"));
            verify(ocrPersistenceService, never()).saveFailure(any(), any());
            verify(ocrPersistenceService, never()).saveGovSuccess(any());
        }

        @Test
        @DisplayName("OCR 추출 실패 → saveFailure(이미지 처리 중 오류) — 예외 메시지는 DB에 남지 않는다")
        void ocrExtractionFails_savesFailure() {
            when(ocrPersistenceService.loadVerification(10L)).thenReturn(mock(IdentityVerification.class));
            when(codefOcrClient.extractIdCard(any()))
                    .thenThrow(new BusinessException(UserErrorCode.OCR_FAILED));

            ocrService.processAsync(imagePath, 10L);

            // 고정 메시지만 저장되어야 함 — 예외 메시지(e.getMessage())가 그대로 DB에 평문 저장되면 안 된다
            verify(ocrPersistenceService).saveFailure(eq(10L), eq("이미지 처리 중 오류가 발생했습니다."));
            verifyNoInteractions(mockGovernmentVerifyService);
        }

        @Test
        @DisplayName("처리 완료 후 임시파일을 삭제한다")
        void deletesTempFileAfterProcessing() {
            when(ocrPersistenceService.loadVerification(10L)).thenReturn(mock(IdentityVerification.class));
            IdCardOcrResult result = new IdCardOcrResult("홍길동", "901201-1234567", "2023-01-15");
            when(codefOcrClient.extractIdCard(any())).thenReturn(result);
            when(mockGovernmentVerifyService.verify(any(), any(), any()))
                    .thenReturn(GovernmentVerifyResult.VERIFIED);

            ocrService.processAsync(imagePath, 10L);

            assertThat(Files.exists(imagePath)).isFalse();
        }
    }

    @Nested
    @DisplayName("로그 마스킹")
    class LoggingMasking {

        private Path imagePath;
        private ListAppender<ILoggingEvent> appender;

        @BeforeEach
        void setUp() throws Exception {
            imagePath = Files.createTempFile("ocr-test-", ".tmp");
            Files.write(imagePath, new byte[]{1});

            appender = new ListAppender<>();
            appender.start();
            Logger logger = (Logger) LoggerFactory.getLogger(OcrService.class);
            logger.setLevel(Level.DEBUG);
            logger.addAppender(appender);
        }

        @AfterEach
        void tearDown() throws Exception {
            ((Logger) LoggerFactory.getLogger(OcrService.class)).detachAppender(appender);
            Files.deleteIfExists(imagePath);
        }

        private String logMessages() {
            StringBuilder sb = new StringBuilder();
            appender.list.forEach(event -> sb.append(event.getFormattedMessage()).append('\n'));
            return sb.toString();
        }

        @Test
        @DisplayName("OCR 1단계 완료 로그 — 이름(PII)은 로그에 남지 않는다")
        void ocrSuccess_doesNotLogName() {
            when(ocrPersistenceService.loadVerification(10L)).thenReturn(mock(IdentityVerification.class));
            IdCardOcrResult result = new IdCardOcrResult("홍길동", "901201-1234567", "2023-01-15");
            when(codefOcrClient.extractIdCard(any())).thenReturn(result);
            when(mockGovernmentVerifyService.verify(any(), any(), any()))
                    .thenReturn(GovernmentVerifyResult.VERIFIED);

            ocrService.processAsync(imagePath, 10L);

            assertThat(logMessages()).doesNotContain("홍길동");
        }

        @Test
        @DisplayName("OCR 처리 오류 로그 — 예외 메시지는 DB(failureReason)에는 남지 않고 로그(error)에만 남는다")
        void ocrExtractionFails_exceptionMessageOnlyInLogNotInDb() {
            when(ocrPersistenceService.loadVerification(10L)).thenReturn(mock(IdentityVerification.class));
            when(codefOcrClient.extractIdCard(any()))
                    .thenThrow(new BusinessException(UserErrorCode.OCR_FAILED));

            ocrService.processAsync(imagePath, 10L);

            verify(ocrPersistenceService).saveFailure(eq(10L), eq("이미지 처리 중 오류가 발생했습니다."));
            assertThat(logMessages()).contains("[OCR] 처리 오류");
        }
    }
}
