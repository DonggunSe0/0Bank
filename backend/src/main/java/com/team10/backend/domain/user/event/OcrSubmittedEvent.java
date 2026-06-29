package com.team10.backend.domain.user.event;

import java.nio.file.Path;

/** OCR 제출 트랜잭션 커밋 후 비동기 처리를 시작하기 위해 발행하는 이벤트. */
public record OcrSubmittedEvent(
        Path tempImagePath,
        Long verificationId,
        // 스레드풀 포화로 processAsync가 시작 못 했을 때 OcrSubmittedEventListener가 직접 락을 해제하기 위해 필요
        Long userId
) {
}
