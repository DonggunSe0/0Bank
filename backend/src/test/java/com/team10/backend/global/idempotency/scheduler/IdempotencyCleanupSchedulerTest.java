package com.team10.backend.global.idempotency.scheduler;

import com.team10.backend.global.idempotency.service.IdempotencyService;
import com.team10.backend.global.lock.DistributedLockTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyCleanupSchedulerTest {

    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private DistributedLockTemplate lockTemplate;

    @InjectMocks
    private IdempotencyCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "staleProcessingTimeoutMinutes", 10L);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 15L);

        when(lockTemplate.tryExecuteWithLock(anyString(), any(Duration.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(2);
                    task.run();
                    return true;
                });
    }

    @Test
    @DisplayName("스케줄러는 10분 이상 방치된 PROCESSING 멱등성 레코드 만료 처리를 호출한다")
    void expireStaleProcessing_callsServiceWithTenMinuteTimeout() {
        scheduler.expireStaleProcessing();

        verify(idempotencyService).expireStaleProcessing(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("스케줄러는 보관 기간이 지난 종료 상태 멱등성 레코드 삭제를 호출한다")
    void deleteOldRecords_callsServiceWithRetentionDays() {
        scheduler.deleteOldRecords();

        verify(idempotencyService).deleteRecordsOlderThan(Duration.ofDays(15));
    }
}
