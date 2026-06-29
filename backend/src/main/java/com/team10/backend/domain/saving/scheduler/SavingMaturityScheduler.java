package com.team10.backend.domain.saving.scheduler;

import com.team10.backend.domain.saving.exception.SavingErrorCode;
import com.team10.backend.domain.saving.service.SavingDepositService;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.lock.DistributedLockTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class SavingMaturityScheduler {

    private static final String LOCK_KEY = "lock:saving:maturity";
    private static final Duration LOCK_WAIT_TIME = Duration.ofSeconds(1);
    private static final Duration LOCK_LEASE_TIME = Duration.ofMinutes(10);

    private final SavingDepositService savingDepositService;
    private final DistributedLockTemplate distributedLockTemplate;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void matureDueSavings() {
        try {
            distributedLockTemplate.executeWithLock(
                    LOCK_KEY,
                    LOCK_WAIT_TIME,
                    LOCK_LEASE_TIME,
                    SavingErrorCode.SAVING_SCHEDULER_LOCK_NOT_ACQUIRED,
                    () -> {
                        int maturedCount = savingDepositService.matureDueSavings();
                        log.info("저축 만기 처리 스케줄러 실행 완료. maturedCount={}",
                                maturedCount);
                        return maturedCount;
                    }
            );
        } catch (BusinessException e) {
            if (e.getErrorCode() == SavingErrorCode.SAVING_SCHEDULER_LOCK_NOT_ACQUIRED)
            {
                log.info("저축 만기 처리 스케줄러 실행 건너뜀. reason=lock_not_acquired");
                return;
            }

            log.warn("저축 만기 처리 스케줄러 실행 실패", e);
        } catch (RuntimeException e) {
            log.warn("저축 만기 처리 스케줄러 실행 실패", e);
        }
    }
}
