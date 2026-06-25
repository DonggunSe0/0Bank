package com.team10.backend.domain.user.verification;

import com.team10.backend.domain.user.event.OneWonTransferRequestedEvent;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OneWonTransferProcessorTest {

    @Mock BankTransferService bankTransferService;
    @Mock OneWonVerificationService oneWonVerificationService;
    @Mock OneWonPersistenceService oneWonPersistenceService;

    @InjectMocks
    OneWonTransferProcessor processor;

    @Nested
    @DisplayName("processAsync")
    class ProcessAsync {

        @Test
        @DisplayName("송금 성공 — 코드 생성 후 송금, 성공 상태 반영, 락 해제")
        void success() {
            OneWonTransferRequestedEvent event =
                    new OneWonTransferRequestedEvent(10L, 1L, "090", "12345678901");
            when(oneWonVerificationService.generateAndStore(10L, 1L)).thenReturn("1234");

            processor.processAsync(event);

            verify(bankTransferService).sendOneWon("090", "12345678901", "1234");
            verify(oneWonPersistenceService).markSent(10L);
            verify(oneWonVerificationService).releaseStartLock(1L);
            verify(oneWonVerificationService, never()).deleteCode(any());
            verify(oneWonPersistenceService, never()).markFailed(any(), any());
        }

        @Test
        @DisplayName("송금 실패 — Redis 코드/카운터 롤백, 재시도 가능 상태로 복구, 락 해제")
        void transferFails_rollsBackAndRevertsToRetryable() {
            OneWonTransferRequestedEvent event =
                    new OneWonTransferRequestedEvent(10L, 1L, "090", "12345678901");
            when(oneWonVerificationService.generateAndStore(10L, 1L)).thenReturn("1234");
            doThrow(new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED))
                    .when(bankTransferService).sendOneWon(any(), any(), any());

            processor.processAsync(event);

            verify(oneWonVerificationService).deleteCode(10L);
            verify(oneWonVerificationService).decrementDailyCount(1L);
            verify(oneWonPersistenceService).markFailed(eq(10L), anyString());
            verify(oneWonPersistenceService, never()).markSent(any());
            verify(oneWonVerificationService).releaseStartLock(1L);
        }

        @Test
        @DisplayName("실패 사유는 고정 메시지만 기록 — 원본 예외 메시지는 DB에 남지 않는다")
        void failureReason_isFixedMessage_notRawExceptionMessage() {
            OneWonTransferRequestedEvent event =
                    new OneWonTransferRequestedEvent(10L, 1L, "090", "12345678901");
            when(oneWonVerificationService.generateAndStore(10L, 1L)).thenReturn("1234");
            doThrow(new RuntimeException("connection reset by peer — internal detail"))
                    .when(bankTransferService).sendOneWon(any(), any(), any());

            processor.processAsync(event);

            verify(oneWonPersistenceService).markFailed(10L, "1원 송금에 실패했습니다. 다시 시도해주세요.");
        }
    }
}
