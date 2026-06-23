package com.team10.backend.domain.investment.marketholiday.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.team10.backend.domain.investment.marketholiday.cache.MarketHolidayCache;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarketStatusValidatorTest {

    private final MarketHolidayCache marketHolidayCache = new MarketHolidayCache();
    private final MarketStatusValidator marketStatusValidator = new MarketStatusValidator(marketHolidayCache);

    @Test
    @DisplayName("휴장일이 아니고 정규장 연속매매 시간 안이면 거래 가능 시간으로 판단한다")
    void isContinuousTradingTime() {
        assertThat(marketStatusValidator.isContinuousTradingTime(
                LocalDateTime.of(2026, 6, 16, 9, 0),
                MarketType.KRX
        )).isTrue();
        assertThat(marketStatusValidator.isContinuousTradingTime(
                LocalDateTime.of(2026, 6, 16, 15, 19, 59),
                MarketType.KRX
        )).isTrue();
    }

    @Test
    @DisplayName("정규장 연속매매 시간 밖이면 거래 가능 시간이 아니라고 판단한다")
    void isContinuousTradingTimeReturnsFalseOutsideTradingHours() {
        assertThat(marketStatusValidator.isContinuousTradingTime(
                LocalDateTime.of(2026, 6, 16, 8, 59, 59),
                MarketType.KRX
        )).isFalse();
        assertThat(marketStatusValidator.isContinuousTradingTime(
                LocalDateTime.of(2026, 6, 16, 15, 20),
                MarketType.KRX
        )).isFalse();
    }

    @Test
    @DisplayName("휴장일이면 정규장 시간 안이어도 거래 가능 시간이 아니라고 판단한다")
    void isContinuousTradingTimeReturnsFalseOnHoliday() {
        LocalDate holiday = LocalDate.of(2026, 6, 17);
        marketHolidayCache.replace(MarketType.KRX, List.of(holiday));

        assertThat(marketStatusValidator.isContinuousTradingTime(
                LocalDateTime.of(2026, 6, 17, 10, 0),
                MarketType.KRX
        )).isFalse();
    }
}
