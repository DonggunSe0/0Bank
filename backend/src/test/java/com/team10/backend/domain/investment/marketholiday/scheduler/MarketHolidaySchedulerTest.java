package com.team10.backend.domain.investment.marketholiday.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.team10.backend.domain.investment.marketholiday.service.MarketHolidaySyncService;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
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

    private MarketHolidayScheduler marketHolidayScheduler;

    @BeforeEach
    void setUp() {
        marketHolidayScheduler = new MarketHolidayScheduler(marketHolidaySyncService);
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
}
