package com.team10.backend.domain.investment.marketholiday.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarketHolidayCacheTest {

    private final MarketHolidayCache marketHolidayCache = new MarketHolidayCache();

    @Test
    @DisplayName("캐시가 비어있으면 휴장일이 아닌 것으로 판단한다")
    void isHolidayReturnsFalseWhenCacheIsEmpty() {
        assertThat(marketHolidayCache.isHoliday(MarketType.KRX, LocalDate.of(2026, 6, 16)))
                .isFalse();
    }

    @Test
    @DisplayName("휴장일 캐시를 교체하면 해당 날짜를 휴장일로 판단한다")
    void replace() {
        LocalDate holiday = LocalDate.of(2026, 6, 17);

        marketHolidayCache.replace(MarketType.KRX, List.of(holiday));

        assertThat(marketHolidayCache.isHoliday(MarketType.KRX, holiday)).isTrue();
        assertThat(marketHolidayCache.isHoliday(MarketType.KRX, LocalDate.of(2026, 6, 18))).isFalse();
    }

    @Test
    @DisplayName("외부 컬렉션 변경이 캐시에 반영되지 않는다")
    void replaceCopiesInputCollection() {
        LocalDate holiday = LocalDate.of(2026, 6, 17);
        List<LocalDate> holidays = new ArrayList<>(List.of(holiday));

        marketHolidayCache.replace(MarketType.KRX, holidays);
        holidays.clear();

        assertThat(marketHolidayCache.isHoliday(MarketType.KRX, holiday)).isTrue();
    }

    @Test
    @DisplayName("같은 시장의 캐시를 다시 교체하면 이전 휴장일 목록을 대체한다")
    void replaceOverwritesSameMarketHolidays() {
        LocalDate oldHoliday = LocalDate.of(2026, 6, 17);
        LocalDate newHoliday = LocalDate.of(2026, 6, 18);

        marketHolidayCache.replace(MarketType.KRX, List.of(oldHoliday));
        marketHolidayCache.replace(MarketType.KRX, List.of(newHoliday));

        assertThat(marketHolidayCache.isHoliday(MarketType.KRX, oldHoliday)).isFalse();
        assertThat(marketHolidayCache.isHoliday(MarketType.KRX, newHoliday)).isTrue();
    }
}
