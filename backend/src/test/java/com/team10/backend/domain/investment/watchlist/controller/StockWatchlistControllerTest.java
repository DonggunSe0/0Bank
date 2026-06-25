package com.team10.backend.domain.investment.watchlist.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.stock.dto.res.StockSummaryRes;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.watchlist.service.StockWatchlistService;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.support.security.AuthenticationPrincipalTestConfig;
import com.team10.backend.support.security.WithMockLongUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockWatchlistController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser
class StockWatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockWatchlistService stockWatchlistService;

    @Test
    @DisplayName("관심 종목 등록 API는 인증 사용자와 종목 ID를 받아 201을 반환한다")
    void addWatchlist() throws Exception {
        when(stockWatchlistService.addWatchlist(1L, 10L)).thenReturn(summary());

        mockMvc.perform(post("/api/v1/investment/watchlists/{stockId}", 10L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.stockCode").value("005930"))
                .andExpect(jsonPath("$.stockName").value("삼성전자"))
                .andExpect(jsonPath("$.market").value("KOSPI"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(stockWatchlistService).addWatchlist(1L, 10L);
    }

    @Test
    @DisplayName("중복 관심 종목 등록 요청은 409를 반환한다")
    void addWatchlistWithDuplicatedStock() throws Exception {
        when(stockWatchlistService.addWatchlist(1L, 10L))
                .thenThrow(new BusinessException(InvestmentErrorCode.WATCHLIST_DUPLICATED));

        mockMvc.perform(post("/api/v1/investment/watchlists/{stockId}", 10L))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("내 관심 종목 목록 조회 API는 인증 사용자의 관심 종목 목록을 반환한다")
    void getWatchlists() throws Exception {
        when(stockWatchlistService.getWatchlists(1L)).thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/v1/investment/watchlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].stockCode").value("005930"))
                .andExpect(jsonPath("$[0].stockName").value("삼성전자"));

        verify(stockWatchlistService).getWatchlists(1L);
    }

    @Test
    @DisplayName("관심 종목 삭제 API는 인증 사용자와 종목 ID를 받아 204를 반환한다")
    void removeWatchlist() throws Exception {
        mockMvc.perform(delete("/api/v1/investment/watchlists/{stockId}", 10L))
                .andExpect(status().isNoContent());

        verify(stockWatchlistService).removeWatchlist(1L, 10L);
    }

    @Test
    @DisplayName("삭제 중 서비스 예외가 발생하면 해당 상태 코드를 반환한다")
    void removeWatchlistWithException() throws Exception {
        doThrow(new BusinessException(InvestmentErrorCode.STOCK_NOT_FOUND))
                .when(stockWatchlistService)
                .removeWatchlist(1L, 10L);

        mockMvc.perform(delete("/api/v1/investment/watchlists/{stockId}", 10L))
                .andExpect(status().isNotFound());
    }

    private StockSummaryRes summary() {
        return new StockSummaryRes(
                10L,
                "005930",
                "삼성전자",
                StockMarket.KOSPI,
                StockStatus.ACTIVE,
                4_000_000L,
                10_000L
        );
    }
}
