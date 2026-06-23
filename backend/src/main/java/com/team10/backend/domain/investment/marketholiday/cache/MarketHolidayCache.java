package com.team10.backend.domain.investment.marketholiday.cache;

import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MarketHolidayCache {

    /**
     * 조회 스레드와 스케줄러 갱신 스레드가 동시에 접근할 수 있으므로 캐시 객체를 수정하지 않고 새 Map을 생성한 뒤 통째로 교체한다. 조회 시점에는 항상 이전 캐시 또는 신규 캐시 중 하나만 보장된다.
     */
    private volatile Map<MarketType, Set<LocalDate>> holidaysByMarket = Map.of();

    public boolean isHoliday(MarketType marketType, LocalDate date) {
        return holidaysByMarket
                .getOrDefault(marketType, Set.of())
                .contains(date);
    }

    /**
     * 새로운 Map을 생성하여 세팅 후 holidaysByMarket의 참조 자체를 변경한다
     */
    public void replace(MarketType marketType, Collection<LocalDate> holidayDates) {
        Map<MarketType, Set<LocalDate>> newCache = new EnumMap<>(MarketType.class);
        newCache.putAll(holidaysByMarket);
        newCache.put(marketType, Set.copyOf(holidayDates));
        holidaysByMarket = Map.copyOf(newCache);
    }
}
