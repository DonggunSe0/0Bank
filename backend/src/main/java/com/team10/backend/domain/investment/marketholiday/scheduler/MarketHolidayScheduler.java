package com.team10.backend.domain.investment.marketholiday.scheduler;

import static com.team10.backend.domain.investment.config.KisConstants.SEOUL_ZONE;

import com.team10.backend.domain.investment.marketholiday.service.MarketHolidaySyncService;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
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

    private final MarketHolidaySyncService marketHolidaySyncService;

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        marketHolidaySyncService.loadCacheFromDatabase(MarketType.KRX);
        syncSafely("startup");
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncDaily() {
        syncSafely("daily");
    }

    private void syncSafely(String trigger) {
        LocalDate baseDate = LocalDate.now(SEOUL_ZONE);
        try {
            marketHolidaySyncService.sync(MarketType.KRX, baseDate);
        } catch (RuntimeException e) {
            log.warn("Market holiday sync failed. trigger={}, baseDate={}", trigger, baseDate, e);
        }
    }
}
