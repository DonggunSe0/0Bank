package com.team10.backend.domain.exchange.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.exchange.dto.req.ExchangeOrderCreateReq;
import com.team10.backend.domain.exchange.dto.req.ExchangeQuoteCreateReq;
import com.team10.backend.domain.exchange.dto.res.ExchangeOrderRes;
import com.team10.backend.domain.exchange.dto.res.ExchangeQuoteRes;
import com.team10.backend.domain.exchange.service.ExchangeRateService;
import com.team10.backend.domain.exchange.service.ExchangeService;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.ExchangeDirection;
import com.team10.backend.domain.exchange.type.ExchangeOrderStatus;
import com.team10.backend.support.security.AuthenticationPrincipalTestConfig;
import com.team10.backend.support.security.WithMockLongUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExchangeController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser(userId = 1L)
class ExchangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ExchangeRateService exchangeRateService;

    @MockitoBean
    private ExchangeService exchangeService;

    @Test
    @DisplayName("환전 견적 생성 API는 인증 사용자와 요청 본문을 받아 201을 반환한다")
    void createExchangeQuote() throws Exception {
        ExchangeQuoteCreateReq request = new ExchangeQuoteCreateReq(
                CurrencyCode.KRW,
                CurrencyCode.USD,
                new BigDecimal("100000")
        );
        ExchangeQuoteRes response = new ExchangeQuoteRes(
                1L,
                CurrencyCode.KRW,
                CurrencyCode.USD,
                new BigDecimal("100000"),
                new BigDecimal("1380.000000"),
                new BigDecimal("0.002500"),
                new BigDecimal("250"),
                new BigDecimal("72.2826"),
                LocalDateTime.of(2026, 6, 16, 15, 5),
                LocalDateTime.of(2026, 6, 16, 15, 0)
        );

        when(exchangeService.createQuote(
                eq(1L),
                eq(CurrencyCode.KRW),
                eq(CurrencyCode.USD),
                eq(new BigDecimal("100000"))
        )).thenReturn(response);

        mockMvc.perform(post("/api/v1/exchanges/currencies/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exchangeQuoteId").value(1L))
                .andExpect(jsonPath("$.fromCurrencyCode").value("KRW"))
                .andExpect(jsonPath("$.toCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.fromAmount").value(100000))
                .andExpect(jsonPath("$.rate").value(1380.000000))
                .andExpect(jsonPath("$.feeRate").value(0.002500))
                .andExpect(jsonPath("$.fee").value(250))
                .andExpect(jsonPath("$.expectedToAmount").value(72.2826));

        verify(exchangeService).createQuote(
                eq(1L),
                eq(CurrencyCode.KRW),
                eq(CurrencyCode.USD),
                eq(new BigDecimal("100000"))
        );
    }

    @Test
    @DisplayName("환전 견적 생성 API는 출금 통화가 없으면 400을 반환한다")
    void createExchangeQuoteWithoutFromCurrency() throws Exception {
        String request = """
                {
                  "toCurrencyCode": "USD",
                  "fromAmount": 100000
                }
                """;

        mockMvc.perform(post("/api/v1/exchanges/currencies/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("환전 견적 생성 API는 환전 금액이 0 이하이면 400을 반환한다")
    void createExchangeQuoteWithNonPositiveAmount() throws Exception {
        ExchangeQuoteCreateReq request = new ExchangeQuoteCreateReq(
                CurrencyCode.KRW,
                CurrencyCode.USD,
                BigDecimal.ZERO
        );

        mockMvc.perform(post("/api/v1/exchanges/currencies/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("환전 주문 실행 API는 인증 사용자와 멱등성 키, 요청 본문을 받아 201을 반환한다")
    void createExchangeOrder() throws Exception {
        ExchangeOrderCreateReq request = new ExchangeOrderCreateReq(
                1L,
                10L,
                20L
        );
        ExchangeOrderRes response = new ExchangeOrderRes(
                100L,
                1L,
                ExchangeDirection.KRW_TO_FOREIGN,
                ExchangeOrderStatus.COMPLETED,
                10L,
                20L,
                new BigDecimal("100000"),
                new BigDecimal("72.2826"),
                new BigDecimal("1380.000000"),
                new BigDecimal("0.002500"),
                new BigDecimal("250.0000"),
                LocalDateTime.of(2026, 6, 21, 10, 0),
                LocalDateTime.of(2026, 6, 21, 10, 0, 1)
        );

        when(exchangeService.createExchangeOrder(
                eq(1L),
                eq("exchange-order-key"),
                eq(1L),
                eq(10L),
                eq(20L)
        )).thenReturn(response);

        mockMvc.perform(post("/api/v1/exchanges/currencies/orders")
                        .header("Idempotency-Key", "exchange-order-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exchangeOrderId").value(100L))
                .andExpect(jsonPath("$.exchangeQuoteId").value(1L))
                .andExpect(jsonPath("$.direction").value("KRW_TO_FOREIGN"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.krwAccountId").value(10L))
                .andExpect(jsonPath("$.fxWalletId").value(20L))
                .andExpect(jsonPath("$.fromAmount").value(100000))
                .andExpect(jsonPath("$.toAmount").value(72.2826));

        verify(exchangeService).createExchangeOrder(
                eq(1L),
                eq("exchange-order-key"),
                eq(1L),
                eq(10L),
                eq(20L)
        );
    }

    @Test
    @DisplayName("환전 주문 실행 API는 멱등성 키가 없으면 400을 반환한다")
    void createExchangeOrderWithoutIdempotencyKey() throws Exception {
        ExchangeOrderCreateReq request = new ExchangeOrderCreateReq(
                1L,
                10L,
                20L
        );

        mockMvc.perform(post("/api/v1/exchanges/currencies/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("환전 주문 상세 조회 API는 인증 사용자와 주문 ID를 받아 200을 반환한다")
    void getExchangeOrder() throws Exception {
        ExchangeOrderRes response = new ExchangeOrderRes(
                100L,
                1L,
                ExchangeDirection.KRW_TO_FOREIGN,
                ExchangeOrderStatus.COMPLETED,
                10L,
                20L,
                new BigDecimal("100000"),
                new BigDecimal("72.2826"),
                new BigDecimal("1380.000000"),
                new BigDecimal("0.002500"),
                new BigDecimal("250.0000"),
                LocalDateTime.of(2026, 6, 21, 10, 0),
                LocalDateTime.of(2026, 6, 21, 10, 0, 1)
        );

        when(exchangeService.getExchangeOrder(eq(1L), eq(100L))).thenReturn(response);

        mockMvc.perform(get("/api/v1/exchanges/currencies/orders/{exchangeOrderId}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeOrderId").value(100L))
                .andExpect(jsonPath("$.exchangeQuoteId").value(1L))
                .andExpect(jsonPath("$.direction").value("KRW_TO_FOREIGN"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.krwAccountId").value(10L))
                .andExpect(jsonPath("$.fxWalletId").value(20L))
                .andExpect(jsonPath("$.fromAmount").value(100000))
                .andExpect(jsonPath("$.toAmount").value(72.2826));

        verify(exchangeService).getExchangeOrder(eq(1L), eq(100L));
    }

    @Test
    @DisplayName("내 환전 주문 목록 조회 API는 인증 사용자의 주문 목록을 200으로 반환한다")
    void getExchangeOrders() throws Exception {
        ExchangeOrderRes first = new ExchangeOrderRes(
                101L,
                11L,
                ExchangeDirection.FOREIGN_TO_KRW,
                ExchangeOrderStatus.COMPLETED,
                10L,
                20L,
                new BigDecimal("10.0000"),
                new BigDecimal("13716.0000"),
                new BigDecimal("1375.000000"),
                new BigDecimal("0.002500"),
                new BigDecimal("34.0000"),
                LocalDateTime.of(2026, 6, 21, 11, 0),
                LocalDateTime.of(2026, 6, 21, 11, 0, 1)
        );
        ExchangeOrderRes second = new ExchangeOrderRes(
                100L,
                1L,
                ExchangeDirection.KRW_TO_FOREIGN,
                ExchangeOrderStatus.COMPLETED,
                10L,
                20L,
                new BigDecimal("100000"),
                new BigDecimal("72.2826"),
                new BigDecimal("1380.000000"),
                new BigDecimal("0.002500"),
                new BigDecimal("250.0000"),
                LocalDateTime.of(2026, 6, 21, 10, 0),
                LocalDateTime.of(2026, 6, 21, 10, 0, 1)
        );

        when(exchangeService.getExchangeOrders(eq(1L))).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/v1/exchanges/currencies/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exchangeOrderId").value(101L))
                .andExpect(jsonPath("$[0].direction").value("FOREIGN_TO_KRW"))
                .andExpect(jsonPath("$[0].fromAmount").value(10.0000))
                .andExpect(jsonPath("$[0].toAmount").value(13716.0000))
                .andExpect(jsonPath("$[1].exchangeOrderId").value(100L))
                .andExpect(jsonPath("$[1].direction").value("KRW_TO_FOREIGN"))
                .andExpect(jsonPath("$[1].fromAmount").value(100000))
                .andExpect(jsonPath("$[1].toAmount").value(72.2826));

        verify(exchangeService).getExchangeOrders(eq(1L));
    }

}
