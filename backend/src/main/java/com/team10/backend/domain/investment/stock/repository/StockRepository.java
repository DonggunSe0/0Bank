package com.team10.backend.domain.investment.stock.repository;

import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByStockCode(String stockCode);

    Optional<Stock> findByStockCodeAndStatus(String stockCode, StockStatus status);

    List<Stock> findAllByStockCodeIn(Collection<String> stockCodes);

    Page<Stock> findAllByMarketAndStatus(StockMarket market, StockStatus status, Pageable pageable);

    @Query("""
            select s
            from Stock s
            where s.market = :market
              and s.status = :status
              and s.stockName like concat('%', :keyword, '%') escape '\\'
            order by s.stockName asc, s.stockCode asc
            """)
    List<Stock> search(
            @Param("keyword") String keyword,
            @Param("market") StockMarket market,
            @Param("status") StockStatus status
    );

    /**
     * 이번 마스터 파일에 존재한 종목의 updatedAt을 동기화 기준 시각으로 맞춘다.
     * 값 변경이 없는 종목도 파일에 존재했다는 사실을 남기기 위한 명시적 갱신이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Stock s
            set s.updatedAt = :syncedAt
            where s.stockCode in :stockCodes
            """)
    int touchSyncedStocks(
            @Param("stockCodes") Collection<String> stockCodes,
            @Param("syncedAt") LocalDateTime syncedAt
    );

    /**
     * 이번 동기화에서 touch되지 않은 종목은 최신 파일에 없던 종목으로 보고 DELISTED 처리한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Stock s
            set s.status = :status,
                s.updatedAt = :syncedAt
            where s.market = :market
              and s.updatedAt < :syncedAt
              and s.status <> :status
            """)
    int markStocksNotSyncedAsDelisted(
            @Param("market") StockMarket market,
            @Param("syncedAt") LocalDateTime syncedAt,
            @Param("status") StockStatus status
    );
}
