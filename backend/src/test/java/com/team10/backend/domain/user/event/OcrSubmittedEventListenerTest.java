package com.team10.backend.domain.user.event;

import com.team10.backend.domain.user.ocr.OcrService;
import com.team10.backend.domain.user.verification.VerificationSessionRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OcrSubmittedEventListenerTest {

    @Mock OcrService ocrService;
    @Mock VerificationSessionRecorder verificationSessionRecorder;

    @InjectMocks
    OcrSubmittedEventListener listener;

    private Path imagePath;

    @BeforeEach
    void setUp() throws IOException {
        imagePath = Files.createTempFile("ocr-test-", ".tmp");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(imagePath);
    }

    @Test
    @DisplayName("정상 처리 — ocrService.processAsync 호출, 거부 처리 로직은 동작하지 않음")
    void handlesNormally() {
        OcrSubmittedEvent event = new OcrSubmittedEvent(imagePath, 10L);

        listener.handle(event);

        verify(ocrService).processAsync(imagePath, 10L);
        verifyNoInteractions(verificationSessionRecorder);
    }

    @Test
    @DisplayName("스레드풀 포화로 OCR 작업 거부 → FAILED로 별도 트랜잭션 기록 + 임시파일 삭제")
    void handlesRejectedExecution() {
        OcrSubmittedEvent event = new OcrSubmittedEvent(imagePath, 10L);
        doThrow(new RejectedExecutionException("queue full"))
                .when(ocrService).processAsync(imagePath, 10L);

        listener.handle(event);

        verify(verificationSessionRecorder).markFailedInNewTransaction(eq(10L), anyString());
        assertThat(Files.exists(imagePath)).isFalse();
    }
}
