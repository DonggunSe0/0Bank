package com.team10.backend.domain.investment.client.marketholiday.dto;

import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import java.time.LocalDate;

public record KisHolidayRow(
        MarketType marketType,
        LocalDate holidayDate,
        Boolean isOpen
) {
}
