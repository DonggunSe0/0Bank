package com.team10.backend.domain.investment.marketholiday.entity;

import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 국내 주식 휴장일 데이터를 저장하는 테이블
 *
 */

@Getter
@Entity
@Table(
        name = "market_holidays",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_market_holidays_market_date",
                columnNames = {"market_type", "holiday_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketHoliday extends BaseEntity {

    @Column(nullable = false)
    private LocalDate holidayDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MarketType marketType;

    public static MarketHoliday create(
            LocalDate holidayDate,
            MarketType marketType
    ) {
        MarketHoliday holiday = new MarketHoliday();
        holiday.holidayDate = holidayDate;
        holiday.marketType = marketType;
        return holiday;
    }

}
