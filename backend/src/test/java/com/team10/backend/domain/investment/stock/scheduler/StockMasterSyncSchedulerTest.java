package com.team10.backend.domain.investment.stock.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockMasterSyncSchedulerTest {

    @Test
    @DisplayName("애플리케이션 시작 시 종목 마스터 동기화를 실행한다")
    void syncOnStartup() {
        StockMasterSyncRetryRunner retryRunner = org.mockito.Mockito.mock(StockMasterSyncRetryRunner.class);
        StockMasterSyncScheduler scheduler = new StockMasterSyncScheduler(retryRunner);

        scheduler.syncOnStartup();

        verify(retryRunner).sync("startup");
    }

    @Test
    @DisplayName("스케줄링 동기화 실패는 스케줄러 밖으로 전파하지 않는다")
    void syncBeforeMarketOpenDoesNotThrowWhenSyncFails() {
        StockMasterSyncRetryRunner retryRunner = org.mockito.Mockito.mock(StockMasterSyncRetryRunner.class);
        StockMasterSyncScheduler scheduler = new StockMasterSyncScheduler(retryRunner);
        doThrow(new RuntimeException("sync failed")).when(retryRunner).sync("before-market-open");

        assertThatCode(() -> scheduler.syncBeforeMarketOpen())
                .doesNotThrowAnyException();

        verify(retryRunner).sync("before-market-open");
    }

    @Test
    @DisplayName("이미 종목 마스터 동기화가 실행 중이면 중복 실행하지 않는다")
    void skipWhenPreviousSyncIsRunning() throws Exception {
        StockMasterSyncRetryRunner retryRunner = org.mockito.Mockito.mock(StockMasterSyncRetryRunner.class);
        StockMasterSyncScheduler scheduler = new StockMasterSyncScheduler(retryRunner);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        doAnswer(invocation -> {
            started.countDown();
            release.await(3, TimeUnit.SECONDS);
            return null;
        }).when(retryRunner).sync("startup");

        try {
            executorService.submit(scheduler::syncOnStartup);
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();

            scheduler.syncAfterMarketClose();

            verify(retryRunner, times(1)).sync("startup");
            verify(retryRunner, times(0)).sync("after-market-close");
        } finally {
            release.countDown();
            executorService.shutdownNow();
        }

        verify(retryRunner, timeout(3_000).times(1)).sync("startup");
    }
}
