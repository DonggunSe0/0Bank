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
public class SavingMaturityScheduler {

    private final SavingDepositService savingDepositService;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void matureDueSavings() {
        try {
            int maturedCount = savingDepositService.matureDueSavings();
            log.info("저축 만기 처리 스케줄러 실행 완료. maturedCount={}",
                    maturedCount);
        } catch (RuntimeException e) {
            log.warn("저축 만기 처리 스케줄러 실행 실패", e);
        }
    }
}
