package com.team10.backend.domain.investment.stock.scheduler;

import com.team10.backend.domain.investment.stock.service.StockMasterSyncService;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockMasterSyncRetryRunner {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final StockMasterSyncService stockMasterSyncService;

    @Retryable(
            retryFor = {
                    BusinessException.class,
                    RestClientException.class
            },
            maxAttempts = MAX_RETRY_ATTEMPTS,
            backoff = @Backoff(delay = 3_000L, multiplier = 2.0)
    )
    public void sync(String trigger) {
        log.info(
                "KOSPI stock master sync attempt started. trigger={}, attempt={}",
                trigger,
                currentAttempt()
        );
        stockMasterSyncService.syncKospiMaster();
    }

    @Recover
    public void recover(RuntimeException e, String trigger) {
        log.warn(
                "KOSPI stock master scheduled sync failed after retries. trigger={}, maxAttempts={}",
                trigger,
                MAX_RETRY_ATTEMPTS,
                e
        );
    }

    private int currentAttempt() {
        if (RetrySynchronizationManager.getContext() == null) {
            return 1;
        }

        return RetrySynchronizationManager.getContext().getRetryCount() + 1;
    }
}
