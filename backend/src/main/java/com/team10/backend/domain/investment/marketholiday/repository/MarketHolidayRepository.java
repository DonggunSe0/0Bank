package com.team10.backend.domain.investment.marketholiday.repository;

import com.team10.backend.domain.investment.marketholiday.entity.MarketHoliday;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketHolidayRepository extends JpaRepository<MarketHoliday, Long> {

    List<MarketHoliday> findAllByMarketType(MarketType marketType);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
                delete
                from MarketHoliday m
                where m.marketType = :marketType
            """)
    void deleteByMarketType(@Param("marketType") MarketType marketType);
}
