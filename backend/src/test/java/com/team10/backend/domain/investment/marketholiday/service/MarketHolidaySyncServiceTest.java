package com.team10.backend.domain.investment.marketholiday.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.client.marketholiday.KisHolidayClient;
import com.team10.backend.domain.investment.client.marketholiday.dto.KisHolidayRow;
import com.team10.backend.domain.investment.marketholiday.cache.MarketHolidayCache;
import com.team10.backend.domain.investment.marketholiday.entity.MarketHoliday;
import com.team10.backend.domain.investment.marketholiday.repository.MarketHolidayRepository;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class MarketHolidaySyncServiceTest {

    @Mock
    private KisHolidayClient kisHolidayClient;

    @Mock
    private MarketHolidayRepository marketHolidayRepository;

    @Mock
    private MarketHolidayCache marketHolidayCache;

    @Mock
    private TransactionTemplate transactionTemplate;

    private MarketHolidaySyncService marketHolidaySyncService;

    @BeforeEach
    void setUp() {
        marketHolidaySyncService = new MarketHolidaySyncService(
                kisHolidayClient,
                marketHolidayRepository,
                marketHolidayCache,
                transactionTemplate
        );
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("KIS 휴장일 응답 중 휴장일만 저장하고 캐시를 교체한다")
    void sync() {
        LocalDate baseDate = LocalDate.of(2026, 6, 16);
        LocalDate openDate = LocalDate.of(2026, 6, 16);
        LocalDate holiday = LocalDate.of(2026, 6, 17);
        LocalDate duplicatedHoliday = LocalDate.of(2026, 6, 18);
        when(kisHolidayClient.fetchHolidays(MarketType.KRX, baseDate))
                .thenReturn(List.of(
                        row(openDate, true),
                        row(holiday, false),
                        row(duplicatedHoliday, false),
                        row(duplicatedHoliday, false)
                ));

        marketHolidaySyncService.sync(MarketType.KRX, baseDate);

        InOrder inOrder = inOrder(marketHolidayRepository);
        inOrder.verify(marketHolidayRepository).deleteByMarketType(MarketType.KRX);
        inOrder.verify(marketHolidayRepository).saveAll(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<MarketHoliday>> holidaysCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(marketHolidayRepository).saveAll(holidaysCaptor.capture());
        assertThat(holidaysCaptor.getValue())
                .extracting(MarketHoliday::getHolidayDate)
                .containsExactlyInAnyOrder(holiday, duplicatedHoliday);

        verify(marketHolidayCache).replace(
                eq(MarketType.KRX),
                eq(Set.of(holiday, duplicatedHoliday))
        );
    }

    @Test
    @DisplayName("KIS 휴장일 조회 실패 시 DB와 캐시를 변경하지 않고 예외를 전파한다")
    void syncFailsWhenClientFails() {
        LocalDate baseDate = LocalDate.of(2026, 6, 16);
        RuntimeException exception = new RuntimeException("api failed");
        when(kisHolidayClient.fetchHolidays(MarketType.KRX, baseDate)).thenThrow(exception);

        assertThatThrownBy(() -> marketHolidaySyncService.sync(MarketType.KRX, baseDate))
                .isSameAs(exception);

        verifyNoInteractions(marketHolidayRepository, marketHolidayCache, transactionTemplate);
    }

    @Test
    @DisplayName("DB에 저장된 휴장일을 메모리 캐시에 로딩한다")
    void loadCacheFromDatabase() {
        LocalDate holiday = LocalDate.of(2026, 6, 17);
        LocalDate anotherHoliday = LocalDate.of(2026, 6, 18);
        when(marketHolidayRepository.findAllByMarketType(MarketType.KRX))
                .thenReturn(List.of(
                        MarketHoliday.create(holiday, MarketType.KRX),
                        MarketHoliday.create(anotherHoliday, MarketType.KRX)
                ));

        marketHolidaySyncService.loadCacheFromDatabase(MarketType.KRX);

        verify(marketHolidayCache).replace(
                eq(MarketType.KRX),
                eq(Set.of(holiday, anotherHoliday))
        );
    }

    private KisHolidayRow row(LocalDate holidayDate, boolean isOpen) {
        return new KisHolidayRow(MarketType.KRX, holidayDate, isOpen);
    }
}
