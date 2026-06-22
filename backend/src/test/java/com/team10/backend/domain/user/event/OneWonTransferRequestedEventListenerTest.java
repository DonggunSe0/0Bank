package com.team10.backend.domain.user.event;

import com.team10.backend.domain.user.verification.OneWonPersistenceService;
import com.team10.backend.domain.user.verification.OneWonTransferProcessor;
import com.team10.backend.domain.user.verification.OneWonVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.RejectedExecutionException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OneWonTransferRequestedEventListenerTest {

    @Mock OneWonTransferProcessor oneWonTransferProcessor;
    @Mock OneWonVerificationService oneWonVerificationService;
    @Mock OneWonPersistenceService oneWonPersistenceService;

    @InjectMocks
    OneWonTransferRequestedEventListener listener;

    @Test
    @DisplayName("정상 처리 — processAsync 호출, 거부 처리 로직은 동작하지 않음")
    void handlesNormally() {
        OneWonTransferRequestedEvent event =
                new OneWonTransferRequestedEvent(10L, 1L, "090", "12345678901");

        listener.handle(event);

        verify(oneWonTransferProcessor).processAsync(event);
        verifyNoInteractions(oneWonVerificationService);
        verifyNoInteractions(oneWonPersistenceService);
    }

    @Test
    @DisplayName("스레드풀 포화로 작업 거부 → 재시도 가능 상태로 복구 + 락 해제")
    void handlesRejectedExecution() {
        OneWonTransferRequestedEvent event =
                new OneWonTransferRequestedEvent(10L, 1L, "090", "12345678901");
        doThrow(new RejectedExecutionException("queue full"))
                .when(oneWonTransferProcessor).processAsync(event);

        listener.handle(event);

        verify(oneWonPersistenceService).markFailed(eq(10L), anyString());
        verify(oneWonVerificationService).releaseStartLock(1L);
    }
}
