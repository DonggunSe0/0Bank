package com.team10.backend.domain.user.event;

import com.team10.backend.domain.user.ocr.OcrService;
import com.team10.backend.domain.user.verification.VerificationSessionRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.RejectedExecutionException;

/** OCR 제출 트랜잭션이 커밋된 후 실제 비동기 OCR 처리를 시작한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrSubmittedEventListener {

    private final OcrService ocrService;
    private final VerificationSessionRecorder verificationSessionRecorder;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OcrSubmittedEvent event) {
        try {
            ocrService.processAsync(event.tempImagePath(), event.verificationId());
        } catch (RejectedExecutionException e) {
            // 스레드풀+큐 포화로 작업 자체가 시작 못 함 — OCR_PENDING에 영구히 멈추지 않도록 별도 트랜잭션으로 FAILED 기록
            log.error("[OCR] 스레드풀 포화로 작업 거부 — verificationId={}", event.verificationId(), e);
            verificationSessionRecorder.markFailedInNewTransaction(
                    event.verificationId(),
                    "서버 처리량이 많아 요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."
            );
            // processAsync가 시작되지 못해 자체적으로 락을 해제하지 못하므로 여기서 직접 해제
            ocrService.releaseLock(event.userId());
            // processAsync가 시작되지 못해 자체적으로 임시파일을 정리하지 못하므로 여기서 직접 삭제
            try {
                Files.deleteIfExists(event.tempImagePath());
            } catch (IOException ioe) {
                log.warn("[OCR] 임시파일 삭제 실패 — path={}", event.tempImagePath(), ioe);
            }
        }
    }
}
