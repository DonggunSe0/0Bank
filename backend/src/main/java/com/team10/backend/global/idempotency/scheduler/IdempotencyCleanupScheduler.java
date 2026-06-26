package com.team10.backend.global.idempotency.scheduler;

import com.team10.backend.global.idempotency.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private final IdempotencyService idempotencyService;

    @Scheduled(fixedDelayString = "${idempotency.cleanup-fixed-delay-ms:60000}")
    public void expireStaleProcessing() {
        idempotencyService.expireStaleProcessing(Duration.ofMinutes(10));
        idempotencyService.deleteRecordsOlderThan(Duration.ofDays(15));
    }
}
