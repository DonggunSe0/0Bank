package com.team10.backend.domain.investment.marketholiday.service;

import com.team10.backend.domain.investment.client.marketholiday.KisHolidayClient;
import com.team10.backend.domain.investment.client.marketholiday.dto.KisHolidayRow;
import com.team10.backend.domain.investment.marketholiday.cache.MarketHolidayCache;
import com.team10.backend.domain.investment.marketholiday.entity.MarketHoliday;
import com.team10.backend.domain.investment.marketholiday.repository.MarketHolidayRepository;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketHolidaySyncService {

    private final KisHolidayClient kisHolidayClient;
    private final MarketHolidayRepository marketHolidayRepository;
    private final MarketHolidayCache marketHolidayCache;
    private final TransactionTemplate transactionTemplate;

    public void sync(MarketType marketType, LocalDate baseDate) {

        /** 휴장일 조회 API를 호출하고, 이 중 휴장일만 필터링한다 */
        List<KisHolidayRow> rows = kisHolidayClient.fetchHolidays(marketType, baseDate);
        Set<LocalDate> holidayDates = rows.stream()
                .filter(row -> !Boolean.TRUE.equals(row.isOpen()))
                .map(KisHolidayRow::holidayDate)
                .collect(Collectors.toUnmodifiableSet());

        /** 해당 휴장일 정보들을 엔티티로 변환한다 */
        List<MarketHoliday> holidays = holidayDates.stream()
                .map(holidayDate -> MarketHoliday.create(holidayDate, marketType))
                .toList();

        /** 트랜잭션 내에서 데이터 삭제 후 최신 데이터 삽입을 수행한다 */
        transactionTemplate.executeWithoutResult(status -> {
            marketHolidayRepository.deleteByMarketType(marketType);
            marketHolidayRepository.saveAll(holidays);
        });

        /** 메모리 캐시 업데이트 */
        marketHolidayCache.replace(marketType, holidayDates);

        log.info("Market holidays synced. marketType={}, baseDate={}, holidayCount={}",
                marketType, baseDate, holidayDates.size());
    }

    /**
     * db에 저장되어있는 휴장일을 기반으로 캐시를 로딩한다. 최초 애플리케이션 시작 시 수행. KIS 서버 장애 발생 시에도 서버 db 데이터 기반 운영
     */
    public void loadCacheFromDatabase(MarketType marketType) {
        Set<LocalDate> holidayDates = marketHolidayRepository.findAllByMarketType(marketType)
                .stream()
                .map(MarketHoliday::getHolidayDate)
                .collect(Collectors.toUnmodifiableSet());

        marketHolidayCache.replace(marketType, holidayDates);

        log.debug("Market holiday cache loaded from database. marketType={}, holidayCount={}",
                marketType, holidayDates.size());
    }
}
