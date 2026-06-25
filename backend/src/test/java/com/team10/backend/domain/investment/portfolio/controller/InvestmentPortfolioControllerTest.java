package com.team10.backend.domain.investment.portfolio.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.portfolio.dto.res.InvestmentHoldingRes;
import com.team10.backend.domain.investment.portfolio.service.InvestmentPortfolioService;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.support.security.AuthenticationPrincipalTestConfig;
import com.team10.backend.support.security.WithMockLongUser;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvestmentPortfolioController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser
class InvestmentPortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestmentPortfolioService investmentPortfolioService;

    @Test
    @DisplayName("투자 계좌 보유 종목 조회 API는 페이지 구조를 반환한다")
    void getHoldings() throws Exception {
        InvestmentHoldingRes response = holding();
        when(investmentPortfolioService.getHoldings(1L, 10L, 0, 20))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/investment/accounts/{accountId}/holdings", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(30L))
                .andExpect(jsonPath("$.content[0].stockCode").value("005930"))
                .andExpect(jsonPath("$.content[0].stockName").value("삼성전자"))
                .andExpect(jsonPath("$.content[0].quantity").value(3L))
                .andExpect(jsonPath("$.content[0].averagePrice").value(70000.00))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(investmentPortfolioService).getHoldings(1L, 10L, 0, 20);
    }

    @Test
    @DisplayName("투자 계좌 보유 종목 조회 API는 페이지 파라미터를 서비스에 전달한다")
    void getHoldingsWithPageParams() throws Exception {
        when(investmentPortfolioService.getHoldings(1L, 10L, 1, 5))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

        mockMvc.perform(get("/api/v1/investment/accounts/{accountId}/holdings", 10L)
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        verify(investmentPortfolioService).getHoldings(1L, 10L, 1, 5);
    }

    @Test
    @DisplayName("내 계좌가 아니거나 해지된 계좌이면 404를 반환한다")
    void getHoldingsWithMissingAccount() throws Exception {
        when(investmentPortfolioService.getHoldings(1L, 10L, 0, 20))
                .thenThrow(new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/investment/accounts/{accountId}/holdings", 10L))
                .andExpect(status().isNotFound());
    }

    private InvestmentHoldingRes holding() {
        return new InvestmentHoldingRes(
                30L,
                20L,
                "005930",
                "삼성전자",
                StockMarket.KOSPI,
                StockStatus.ACTIVE,
                3L,
                new BigDecimal("70000.00"),
                4_000_000L,
                10_000L
        );
    }
}
