package com.team10.backend.domain.investment.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.stock.dto.res.StockDetailRes;
import com.team10.backend.domain.investment.stock.dto.res.StockSummaryRes;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.repository.StockRepository;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockSortType;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import com.team10.backend.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class StockQueryServiceTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockQueryService stockQueryService;

    @Test
    @DisplayName("검색어가 포함된 ACTIVE 종목을 종목명 기준으로 조회한다")
    void search() {
        Stock samsung = stock("005930", "삼성전자", 1_000_000L, 10_000L);
        when(stockRepository.search("삼성", StockMarket.KOSPI, StockStatus.ACTIVE))
                .thenReturn(List.of(samsung));

        List<StockSummaryRes> result = stockQueryService.search(" 삼성 ", StockMarket.KOSPI);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().stockCode()).isEqualTo("005930");
        assertThat(result.getFirst().stockName()).isEqualTo("삼성전자");

        verify(stockRepository).search("삼성", StockMarket.KOSPI, StockStatus.ACTIVE);
    }

    @Test
    @DisplayName("검색어가 2글자 미만이면 DB를 조회하지 않고 빈 목록을 반환한다")
    void searchWithBlankKeyword() {
        List<StockSummaryRes> result = stockQueryService.search("삼", StockMarket.KOSPI);

        assertThat(result).isEmpty();
        verifyNoInteractions(stockRepository);
    }

    @Test
    @DisplayName("검색어 LIKE 와일드카드를 이스케이프하고 시장 기본값을 적용한다")
    void searchEscapesLikeWildcardsAndUsesDefaultMarket() {
        Stock samsung = stock("005930", "삼성%전자", 1_000_000L, 10_000L);
        when(stockRepository.search("삼성\\%\\_", StockMarket.KOSPI, StockStatus.ACTIVE))
                .thenReturn(List.of(samsung));

        List<StockSummaryRes> result = stockQueryService.search(" 삼성%_ ", null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().stockName()).isEqualTo("삼성%전자");

        verify(stockRepository).search("삼성\\%\\_", StockMarket.KOSPI, StockStatus.ACTIVE);
    }

    @Test
    @DisplayName("시장과 상태 기준으로 종목 목록을 페이지 조회한다")
    void getStocks() {
        Stock samsung = stock("005930", "삼성전자", 1_000_000L, 10_000L);
        when(stockRepository.findAllByMarketAndStatus(eq(StockMarket.KOSPI), eq(StockStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(samsung)));

        Page<StockSummaryRes> result = stockQueryService.getStocks(
                StockMarket.KOSPI,
                StockStatus.ACTIVE,
                StockSortType.STOCK_NAME,
                Sort.Direction.ASC,
                0,
                20
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().stockCode()).isEqualTo("005930");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(stockRepository).findAllByMarketAndStatus(eq(StockMarket.KOSPI), eq(StockStatus.ACTIVE), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("stockName").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("목록 조회 입력값이 null이면 기본 시장, 상태, 정렬을 적용한다")
    void getStocksWithNullInputs() {
        Stock samsung = stock("005930", "삼성전자", 1_000_000L, 10_000L);
        when(stockRepository.findAllByMarketAndStatus(eq(StockMarket.KOSPI), eq(StockStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(samsung)));

        Page<StockSummaryRes> result = stockQueryService.getStocks(null, null, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(stockRepository).findAllByMarketAndStatus(eq(StockMarket.KOSPI), eq(StockStatus.ACTIVE), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getSort().getOrderFor("stockName").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("정렬 기준에 맞는 DB 필드 방향으로 종목 목록을 페이지 조회한다")
    void getStocksWithSort() {
        Stock samsung = stock("005930", "삼성전자", 1_000_000L, 10_000L);
        when(stockRepository.findAllByMarketAndStatus(eq(StockMarket.KOSPI), eq(StockStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(samsung)));

        Page<StockSummaryRes> result = stockQueryService.getStocks(
                StockMarket.KOSPI,
                StockStatus.ACTIVE,
                StockSortType.MARKET_CAP,
                Sort.Direction.DESC,
                0,
                20
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().marketCap()).isEqualTo(1_000_000L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(stockRepository).findAllByMarketAndStatus(eq(StockMarket.KOSPI), eq(StockStatus.ACTIVE), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("marketCap").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("종목코드로 상세 정보를 조회한다")
    void getStock() {
        Stock samsung = stock("005930", "삼성전자", 1_000_000L, 10_000L);
        when(stockRepository.findByStockCodeAndStatus("005930", StockStatus.ACTIVE)).thenReturn(Optional.of(samsung));

        StockDetailRes result = stockQueryService.getStock("005930");

        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(result.stockName()).isEqualTo("삼성전자");
        assertThat(result.marketCap()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("종목코드에 해당하는 종목이 없으면 예외가 발생한다")
    void getStockWithMissingStock() {
        when(stockRepository.findByStockCodeAndStatus("005930", StockStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockQueryService.getStock("005930"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("상세 조회 종목코드가 비어 있으면 DB를 조회하지 않고 예외가 발생한다")
    void getStockWithBlankStockCode() {
        assertThatThrownBy(() -> stockQueryService.getStock(" "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.STOCK_NOT_FOUND);

        verifyNoInteractions(stockRepository);
    }

    private Stock stock(String stockCode, String stockName, Long marketCap, Long previousVolume) {
        return Stock.create(
                stockCode,
                "KR7" + stockCode + "0000",
                stockName,
                StockMarket.KOSPI,
                CurrencyCode.KRW,
                StockStatus.ACTIVE,
                LocalDate.of(1975, 6, 11),
                1_000_000L,
                2_000_000L,
                3_000_000L,
                marketCap,
                previousVolume
        );
    }
}
