package com.team10.backend.domain.investment.marketholiday.event;

import com.team10.backend.domain.investment.marketholiday.type.MarketType;

public record MarketHolidayChangedEvent(
        MarketType marketType
) {

    public static MarketHolidayChangedEvent changed(MarketType marketType) {
        return new MarketHolidayChangedEvent(marketType);
    }
}
