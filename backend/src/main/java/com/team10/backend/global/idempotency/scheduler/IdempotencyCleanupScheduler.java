package com.team10.backend.global.idempotency.scheduler;

import com.team10.backend.global.idempotency.service.IdempotencyService;
import com.team10.backend.global.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    @Value("${idempotency.stale-processing-timeout-minutes:10}")
    private long staleProcessingTimeoutMinutes;
    @Value("${idempotency.retention-days:15}")
    private long retentionDays;

    private static final String EXPIRE_STALE_PROCESSING_LOCK_KEY =
            "lock:scheduler:idempotency-expire-stale-processing";
    private static final String DELETE_OLD_RECORDS_LOCK_KEY =
            "lock:scheduler:idempotency-delete-old-records";
    private static final Duration LOCK_LEASE_TIME = Duration.ofMinutes(5);

    private final IdempotencyService idempotencyService;
    private final DistributedLockTemplate lockTemplate;

    @Scheduled(fixedDelayString = "${idempotency.cleanup-fixed-delay-ms:60000}")
    public void expireStaleProcessing() {
        try {
            // PROCESSING 상태가 오래 지속된 레코드는 짧은 주기로 만료 처리한다.
            // 멀티 인스턴스 환경에서는 하나의 인스턴스만 처리하고 나머지는 정상적으로 건너뛴다.
            boolean executed = lockTemplate.tryExecuteWithLock(
                    EXPIRE_STALE_PROCESSING_LOCK_KEY,
                    LOCK_LEASE_TIME,
                    () -> {
                        int expiredCount = idempotencyService.expireStaleProcessing(Duration.ofMinutes(staleProcessingTimeoutMinutes));
                        log.info("오래된 PROCESSING 멱등성 레코드 만료 처리 완료. expiredCount={}", expiredCount);
                    }
            );

            if (!executed) {
                log.info("다른 인스턴스에서 오래된 PROCESSING 멱등성 레코드 만료 처리가 실행 중이므로 이번 실행을 건너뜁니다.");
            }
        } catch (RuntimeException e) {
            log.warn("오래된 PROCESSING 멱등성 레코드 만료 처리 실패", e);
        }
    }

    @Scheduled(cron = "${idempotency.delete-old-records-cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void deleteOldRecords() {
        try {
            // 보관 기간이 지난 종료 상태 레코드는 하루에 한 번 삭제한다.
            // 대량 삭제 작업이 중복 실행되지 않도록 별도 락을 사용한다.
            boolean executed = lockTemplate.tryExecuteWithLock(
                    DELETE_OLD_RECORDS_LOCK_KEY,
                    LOCK_LEASE_TIME,
                    () -> {
                        int deletedCount = idempotencyService.deleteRecordsOlderThan(Duration.ofDays(retentionDays));
                        log.info("보관 기간이 지난 멱등성 레코드 삭제 완료. deletedCount={}", deletedCount);
                    }
            );

            if (!executed) {
                log.info("다른 인스턴스에서 보관 기간이 지난 멱등성 레코드 삭제가 실행 중이므로 이번 실행을 건너뜁니다.");
            }
        } catch (RuntimeException e) {
            log.warn("보관 기간이 지난 멱등성 레코드 삭제 실패", e);
        }
    }
}
