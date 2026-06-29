package com.team10.backend.domain.investment.marketholiday.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.marketholiday.service.MarketHolidaySyncService;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import com.team10.backend.global.lock.DistributedLockTemplate;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketHolidaySchedulerTest {

    @Mock
    private MarketHolidaySyncService marketHolidaySyncService;

    @Mock
    private DistributedLockTemplate distributedLockTemplate;

    private MarketHolidayScheduler marketHolidayScheduler;

    @BeforeEach
    void setUp() {
        lenient().when(distributedLockTemplate.tryExecuteWithLock(anyString(), any(Duration.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(2);
                    task.run();
                    return true;
                });
        marketHolidayScheduler = new MarketHolidayScheduler(marketHolidaySyncService, distributedLockTemplate);
    }

    @Test
    @DisplayName("애플리케이션 시작 시 DB 캐시 로딩 후 KIS 휴장일 동기화를 수행한다")
    void syncOnStartup() {
        marketHolidayScheduler.syncOnStartup();

        InOrder inOrder = inOrder(marketHolidaySyncService);
        inOrder.verify(marketHolidaySyncService).loadCacheFromDatabase(MarketType.KRX);
        inOrder.verify(marketHolidaySyncService).sync(eq(MarketType.KRX), any(LocalDate.class));
    }

    @Test
    @DisplayName("스케줄링 휴장일 동기화 실패는 스케줄러 밖으로 전파하지 않는다")
    void syncDailyDoesNotThrowWhenSyncFails() {
        doThrow(new RuntimeException("sync failed"))
                .when(marketHolidaySyncService)
                .sync(eq(MarketType.KRX), any(LocalDate.class));

        assertThatCode(() -> marketHolidayScheduler.syncDaily())
                .doesNotThrowAnyException();

        verify(marketHolidaySyncService).sync(eq(MarketType.KRX), any(LocalDate.class));
    }

    @Test
    @DisplayName("다른 인스턴스가 휴장일 동기화 분산락을 보유 중이면 동기화를 실행하지 않는다")
    void syncDailySkipsWhenDistributedLockIsHeldByAnotherInstance() {
        when(distributedLockTemplate.tryExecuteWithLock(anyString(), any(Duration.class), any(Runnable.class)))
                .thenReturn(false);

        marketHolidayScheduler.syncDaily();

        verify(marketHolidaySyncService, never()).sync(eq(MarketType.KRX), any(LocalDate.class));
    }
}
