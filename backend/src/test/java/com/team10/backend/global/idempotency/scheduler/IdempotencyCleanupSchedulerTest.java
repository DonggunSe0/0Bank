package com.team10.backend.global.idempotency.scheduler;

import com.team10.backend.global.idempotency.service.IdempotencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupSchedulerTest {

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private IdempotencyCleanupScheduler scheduler;

    @Test
    @DisplayName("스케줄러는 10분 이상 방치된 PROCESSING 멱등성 레코드 만료 처리를 호출한다")
    void expireStaleProcessing_callsServiceWithTenMinuteTimeout() {
        scheduler.expireStaleProcessing();

        verify(idempotencyService).expireStaleProcessing(Duration.ofMinutes(10));
    }
}
