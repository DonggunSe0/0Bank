package com.team10.backend.domain.investment.marketholiday.scheduler;

import static com.team10.backend.domain.investment.config.KisConstants.SEOUL_ZONE;

import com.team10.backend.domain.investment.marketholiday.service.MarketHolidaySyncService;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import com.team10.backend.global.lock.DistributedLockTemplate;
import java.time.Duration;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class MarketHolidayScheduler {

    private static final String KRX_MARKET_HOLIDAY_SYNC_LOCK_KEY = "lock:investment:market-holiday:KRX";
    private static final Duration KRX_MARKET_HOLIDAY_SYNC_LOCK_LEASE_TIME = Duration.ofMinutes(5);

    private final MarketHolidaySyncService marketHolidaySyncService;
    private final DistributedLockTemplate distributedLockTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        marketHolidaySyncService.loadCacheFromDatabase(MarketType.KRX);
        syncWithDistributedLock("startup");
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncDaily() {
        syncWithDistributedLock("daily");
    }

    private void syncWithDistributedLock(String trigger) {
        LocalDate baseDate = LocalDate.now(SEOUL_ZONE);
        boolean executed;
        try {
            executed = distributedLockTemplate.tryExecuteWithLock(
                    KRX_MARKET_HOLIDAY_SYNC_LOCK_KEY,
                    KRX_MARKET_HOLIDAY_SYNC_LOCK_LEASE_TIME,
                    () -> marketHolidaySyncService.sync(MarketType.KRX, baseDate)
            );
        } catch (RuntimeException e) {
            log.warn("Market holiday sync failed. trigger={}, baseDate={}", trigger, baseDate, e);
            return;
        }

        if (!executed) {
            log.info("Market holiday sync skipped because another instance holds distributed lock. trigger={}, baseDate={}",
                    trigger,
                    baseDate);
        }
    }
}
