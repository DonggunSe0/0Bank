package com.team10.backend.domain.investment.stock.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.stock.dto.res.StockDetailRes;
import com.team10.backend.domain.investment.stock.dto.res.StockSummaryRes;
import com.team10.backend.domain.investment.stock.service.StockQueryService;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockSortType;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.support.security.AuthenticationPrincipalTestConfig;
import com.team10.backend.support.security.WithMockLongUser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockQueryService stockQueryService;

    @Test
    @DisplayName("주식 종목 검색 API는 키워드가 포함된 종목명 검색 결과를 반환한다")
    void searchStocks() throws Exception {
        StockSummaryRes response = summary();
        when(stockQueryService.search("삼성", StockMarket.KOSPI)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/investment/stocks/search")
                        .param("keyword", "삼성"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stockCode").value("005930"))
                .andExpect(jsonPath("$[0].stockName").value("삼성전자"))
                .andExpect(jsonPath("$[0].market").value("KOSPI"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].marketCap").value(1_000_000L))
                .andExpect(jsonPath("$[0].previousVolume").value(10_000L));

        verify(stockQueryService).search("삼성", StockMarket.KOSPI);
    }

    @Test
    @DisplayName("주식 종목 검색 API는 검색어가 없으면 빈 검색어로 조회한다")
    void searchStocksWithoutKeyword() throws Exception {
        when(stockQueryService.search("", StockMarket.KOSPI)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/investment/stocks/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(stockQueryService).search("", StockMarket.KOSPI);
    }

    @Test
    @DisplayName("주식 종목 목록 조회 API는 페이지 구조를 반환한다")
    void getStocks() throws Exception {
        StockSummaryRes response = summary();
        when(stockQueryService.getStocks(
                StockMarket.KOSPI,
                StockStatus.ACTIVE,
                StockSortType.STOCK_NAME,
                Sort.Direction.ASC,
                0,
                20
        ))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/investment/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].stockCode").value("005930"))
                .andExpect(jsonPath("$.content[0].stockName").value("삼성전자"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(stockQueryService).getStocks(
                StockMarket.KOSPI,
                StockStatus.ACTIVE,
                StockSortType.STOCK_NAME,
                Sort.Direction.ASC,
                0,
                20
        );
    }

    @Test
    @DisplayName("주식 종목 목록 조회 API는 정렬 기준과 방향을 서비스에 전달한다")
    void getStocksWithSort() throws Exception {
        StockSummaryRes response = summary();
        when(stockQueryService.getStocks(
                StockMarket.KOSPI,
                StockStatus.ACTIVE,
                StockSortType.MARKET_CAP,
                Sort.Direction.DESC,
                0,
                20
        ))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/investment/stocks")
                        .param("sort", "MARKET_CAP")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].stockCode").value("005930"));

        verify(stockQueryService).getStocks(
                StockMarket.KOSPI,
                StockStatus.ACTIVE,
                StockSortType.MARKET_CAP,
                Sort.Direction.DESC,
                0,
                20
        );
    }

    @Test
    @DisplayName("주식 종목 상세 조회 API는 종목코드 기준 상세 정보를 반환한다")
    void getStock() throws Exception {
        StockDetailRes response = detail();
        when(stockQueryService.getStock("005930")).thenReturn(response);

        mockMvc.perform(get("/api/v1/investment/stocks/{stockCode}", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.stockCode").value("005930"))
                .andExpect(jsonPath("$.standardCode").value("KR7005930003"))
                .andExpect(jsonPath("$.stockName").value("삼성전자"))
                .andExpect(jsonPath("$.market").value("KOSPI"))
                .andExpect(jsonPath("$.currencyCode").value("KRW"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.listedDate").value("1975-06-11"))
                .andExpect(jsonPath("$.marketCap").value(1_000_000L))
                .andExpect(jsonPath("$.previousVolume").value(10_000L));

        verify(stockQueryService).getStock("005930");
    }

    @Test
    @DisplayName("상세 조회 대상 종목이 없으면 404를 반환한다")
    void getStockWithMissingStock() throws Exception {
        when(stockQueryService.getStock("005930"))
                .thenThrow(new BusinessException(InvestmentErrorCode.STOCK_NOT_FOUND));

        mockMvc.perform(get("/api/v1/investment/stocks/{stockCode}", "005930"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("검색어가 2글자 미만이면 빈 목록을 반환한다")
    void searchStocksWithShortKeyword() throws Exception {
        when(stockQueryService.search("삼", StockMarket.KOSPI)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/investment/stocks/search")
                        .param("keyword", "삼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(stockQueryService).search("삼", StockMarket.KOSPI);
    }

    @Test
    @DisplayName("목록 조회 정렬 기준 enum 값이 아니면 400을 반환한다")
    void getStocksWithInvalidSortType() throws Exception {
        mockMvc.perform(get("/api/v1/investment/stocks")
                        .param("sort", "OPERATING_PROFIT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("목록 조회 page가 음수이면 400을 반환한다")
    void getStocksWithInvalidPage() throws Exception {
        mockMvc.perform(get("/api/v1/investment/stocks")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    private StockSummaryRes summary() {
        return new StockSummaryRes(
                1L,
                "005930",
                "삼성전자",
                StockMarket.KOSPI,
                StockStatus.ACTIVE,
                1_000_000L,
                10_000L
        );
    }

    private StockDetailRes detail() {
        return new StockDetailRes(
                1L,
                "005930",
                "KR7005930003",
                "삼성전자",
                StockMarket.KOSPI,
                CurrencyCode.KRW,
                StockStatus.ACTIVE,
                LocalDate.of(1975, 6, 11),
                1_000_000L,
                2_000_000L,
                3_000_000L,
                1_000_000L,
                10_000L,
                LocalDateTime.of(2026, 6, 22, 10, 0)
        );
    }
}
