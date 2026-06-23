package com.team10.backend.domain.investment.marketholiday.util;

import com.team10.backend.domain.investment.marketholiday.cache.MarketHolidayCache;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketStatusValidator {

    public static final LocalTime CONTINUOUS_MARKET_OPEN = LocalTime.of(9, 0);
    public static final LocalTime CONTINUOUS_MARKET_CLOSE = LocalTime.of(15, 20);

    private final MarketHolidayCache marketHolidayCache;

    public boolean isContinuousTradingTime(LocalDateTime dateTime, MarketType marketType) {
        LocalDate date = dateTime.toLocalDate();
        LocalTime time = dateTime.toLocalTime();

        return isOpenDate(date, marketType)
                && !time.isBefore(CONTINUOUS_MARKET_OPEN)
                && time.isBefore(CONTINUOUS_MARKET_CLOSE);
    }

    private boolean isOpenDate(LocalDate date, MarketType marketType) {
        return !marketHolidayCache.isHoliday(marketType, date);
    }

}
