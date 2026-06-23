package com.team10.backend.domain.investment.trade.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.trade.dto.req.MarketOrderCreateReq;
import com.team10.backend.domain.investment.trade.dto.res.InvestmentTradeRes;
import com.team10.backend.domain.investment.trade.service.InvestmentTradeService;
import com.team10.backend.domain.investment.trade.type.InvestmentTradeType;
import com.team10.backend.support.security.AuthenticationPrincipalTestConfig;
import com.team10.backend.support.security.WithMockLongUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvestmentTradeController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser
class InvestmentTradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private InvestmentTradeService investmentTradeService;

    @Test
    @DisplayName("시장가 주문 API는 인증 사용자와 멱등성 키를 받아 201을 반환한다")
    void createMarketOrder() throws Exception {
        MarketOrderCreateReq request = new MarketOrderCreateReq(
                10L,
                20L,
                "stream-1",
                InvestmentTradeType.BUY,
                2L,
                "123456",
                70_000L
        );
        InvestmentTradeRes response = new InvestmentTradeRes(
                100L,
                10L,
                20L,
                "005930",
                "삼성전자",
                InvestmentTradeType.BUY,
                2L,
                70_000L,
                140_000L,
                70_000L,
                0,
                LocalDateTime.of(2026, 6, 23, 12, 0),
                LocalDateTime.of(2026, 6, 23, 12, 0, 1)
        );
        when(investmentTradeService.createMarketOrder(1L, "order-key-1", request))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/investment/trades/market-orders")
                        .header("Idempotency-Key", "order-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.stockCode").value("005930"))
                .andExpect(jsonPath("$.tradeType").value("BUY"))
                .andExpect(jsonPath("$.executionPrice").value(70_000L))
                .andExpect(jsonPath("$.totalAmount").value(140_000L));

        verify(investmentTradeService).createMarketOrder(1L, "order-key-1", request);
    }
}
