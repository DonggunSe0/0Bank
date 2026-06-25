package com.team10.backend.domain.saving.scheduler;

import com.team10.backend.domain.saving.service.SavingDepositService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class InstallmentPaymentRetryScheduler {

    private final SavingDepositService savingDepositService;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void retryFailedInstallmentPayments() {
        try {
            int retryCount = savingDepositService.retryFailedInstallmentPayments();
            log.info("적금 자동이체 재시도 스케줄러 실행 완료. retryCount={}",
                    retryCount);
        } catch (RuntimeException e) {
            log.warn("적금 자동이체 재시도 스케줄러 실행 실패", e);
        }
    }
}
