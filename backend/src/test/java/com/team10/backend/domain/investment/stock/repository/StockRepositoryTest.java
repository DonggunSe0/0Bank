package com.team10.backend.domain.investment.stock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import com.team10.backend.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class StockRepositoryTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        persistStock("005930", "삼성전자", StockMarket.KOSPI, StockStatus.ACTIVE);
        persistStock("000001", "005테스트", StockMarket.KOSPI, StockStatus.ACTIVE);
        persistStock("000002", "삼성중공업", StockMarket.KOSPI, StockStatus.SUSPENDED);
        persistStock("000003", "삼%전자", StockMarket.KOSPI, StockStatus.ACTIVE);
        persistStock("000004", "삼_전자", StockMarket.KOSPI, StockStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("검색어가 포함된 ACTIVE 종목을 종목명 기준으로 조회한다")
    void searchByStockName() {
        List<Stock> result = stockRepository.search("삼성", StockMarket.KOSPI, StockStatus.ACTIVE);

        assertThat(result)
                .extracting(Stock::getStockName)
                .containsExactly("삼성전자");
    }

    @Test
    @DisplayName("검색어가 종목코드에만 포함되면 검색 결과에서 제외한다")
    void searchDoesNotMatchStockCode() {
        List<Stock> result = stockRepository.search("005", StockMarket.KOSPI, StockStatus.ACTIVE);

        assertThat(result)
                .extracting(Stock::getStockName)
                .containsExactly("005테스트");
    }

    @Test
    @DisplayName("검색어에 포함된 퍼센트 문자는 LIKE 와일드카드가 아닌 일반 문자로 처리한다")
    void searchTreatsPercentAsLiteral() {
        List<Stock> result = stockRepository.search("삼\\%", StockMarket.KOSPI, StockStatus.ACTIVE);

        assertThat(result)
                .extracting(Stock::getStockName)
                .containsExactly("삼%전자");
    }

    @Test
    @DisplayName("검색어에 포함된 언더스코어 문자는 LIKE 와일드카드가 아닌 일반 문자로 처리한다")
    void searchTreatsUnderscoreAsLiteral() {
        List<Stock> result = stockRepository.search("삼\\_", StockMarket.KOSPI, StockStatus.ACTIVE);

        assertThat(result)
                .extracting(Stock::getStockName)
                .containsExactly("삼_전자");
    }

    private void persistStock(
            String stockCode,
            String stockName,
            StockMarket market,
            StockStatus status
    ) {
        Stock stock = Stock.create(
                stockCode,
                "KR7" + stockCode + "0000",
                stockName,
                market,
                CurrencyCode.KRW,
                status,
                LocalDate.of(2026, 1, 1),
                1_000_000L,
                2_000_000L,
                3_000_000L,
                4_000_000L,
                5_000L
        );
        entityManager.persist(stock);
    }
}
