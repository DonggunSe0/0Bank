package com.team10.backend.domain.exchange.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.exchange.dto.req.FxWalletCreateReq;
import com.team10.backend.domain.exchange.dto.res.FxWalletRes;
import com.team10.backend.domain.exchange.service.FxWalletService;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.FxWalletStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FxWalletController.class)
class FxWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private FxWalletService fxWalletService;

    @Test
    @DisplayName("외화 지갑 생성 API는 userId와 요청 본문을 받아 201을 반환한다")
    void createFxWallet() throws Exception {
        FxWalletCreateReq request = new FxWalletCreateReq(CurrencyCode.USD);
        FxWalletRes response = walletResponse(10L, CurrencyCode.USD, BigDecimal.ZERO, FxWalletStatus.ACTIVE);

        when(fxWalletService.createFxWallet(eq(CurrencyCode.USD), eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/fx-wallets")
                        .param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walletId").value(10L))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(fxWalletService).createFxWallet(CurrencyCode.USD, 1L);
    }

    @Test
    @DisplayName("외화 지갑 생성 API는 통화 코드가 없으면 400을 반환한다")
    void createFxWalletWithoutCurrencyCode() throws Exception {
        FxWalletCreateReq request = new FxWalletCreateReq(null);

        mockMvc.perform(post("/api/v1/fx-wallets")
                        .param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내 외화 지갑 목록 조회 API는 userId로 외화 지갑 목록을 반환한다")
    void getFxWallets() throws Exception {
        FxWalletRes response = walletResponse(10L, CurrencyCode.USD, BigDecimal.ZERO, FxWalletStatus.ACTIVE);

        when(fxWalletService.getFxWallets(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/fx-wallets")
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].walletId").value(10L))
                .andExpect(jsonPath("$[0].currencyCode").value("USD"))
                .andExpect(jsonPath("$[0].balance").value(0))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(fxWalletService).getFxWallets(1L);
    }

    @Test
    @DisplayName("외화 지갑 상세 조회 API는 userId와 fxWalletId로 외화 지갑 상세를 반환한다")
    void getFxWallet() throws Exception {
        FxWalletRes response = walletResponse(10L, CurrencyCode.USD, BigDecimal.ZERO, FxWalletStatus.ACTIVE);

        when(fxWalletService.getFxWallet(10L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/fx-wallets/{fxWalletId}", 10L)
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(10L))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(fxWalletService).getFxWallet(10L, 1L);
    }

    @Test
    @DisplayName("외화 지갑 해지 API는 userId와 fxWalletId를 받아 CLOSED 상태의 외화 지갑을 반환한다")
    void closeFxWallet() throws Exception {
        FxWalletRes response = walletResponse(10L, CurrencyCode.USD, BigDecimal.ZERO, FxWalletStatus.CLOSED);

        when(fxWalletService.closeFxWallet(10L, 1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/fx-wallets/{fxWalletId}/close", 10L)
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(10L))
                .andExpect(jsonPath("$.status").value("CLOSED"));

        verify(fxWalletService).closeFxWallet(10L, 1L);
    }

    private FxWalletRes walletResponse(
            Long walletId,
            CurrencyCode currencyCode,
            BigDecimal balance,
            FxWalletStatus status
    ) {
        return new FxWalletRes(
                walletId,
                currencyCode,
                balance,
                status,
                LocalDateTime.of(2026, 6, 8, 15, 45),
                LocalDateTime.of(2026, 6, 8, 15, 45)
        );
    }
}
