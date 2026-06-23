package com.team10.backend.domain.investment.stock.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
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
public class StockMasterSyncScheduler {

    private final StockMasterSyncRetryRunner stockMasterSyncRetryRunner;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 애플리케이션 최초 시작 시 종목 DB가 비어있거나 오래된 상태일 수 있으므로 1회 최신화한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        syncSafely("startup");
    }

    /**
     * 정규장 시작 전 최신 종목 정보를 반영한다.
     */
    @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Seoul")
    public void syncBeforeMarketOpen() {
        syncSafely("before-market-open");
    }

    /**
     * 정규장 종료 후 당일 변경 가능성이 있는 종목 정보를 한 번 더 반영한다.
     */
    @Scheduled(cron = "0 50 17 * * *", zone = "Asia/Seoul")
    public void syncAfterMarketClose() {
        syncSafely("after-market-close");
    }

    private void syncSafely(String trigger) {
        /** 애플리케이션 로딩 시 최초 실행과 스케줄링에 의한 실행의 중복 수행을 방지한다 */
        if (!running.compareAndSet(false, true)) {
            log.info("KOSPI stock master sync skipped because previous sync is still running. trigger={}", trigger);
            return;
        }

        try {
            stockMasterSyncRetryRunner.sync(trigger);
        } catch (RuntimeException e) {
            /** 스케줄러 예외가 다음 실행을 막지 않도록 여기서 처리하고, 상세 원인은 서비스 로그에서 확인한다. */
            log.warn("KOSPI stock master scheduled sync failed. trigger={}", trigger, e);
        } finally {
            running.set(false);
        }
    }
}
