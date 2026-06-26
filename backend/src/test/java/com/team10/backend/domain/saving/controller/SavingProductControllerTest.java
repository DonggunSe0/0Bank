package com.team10.backend.domain.saving.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team10.backend.domain.saving.dto.res.SavingProductRes;
import com.team10.backend.domain.saving.dto.res.SavingProductSummaryRes;
import com.team10.backend.domain.saving.service.SavingProductService;
import com.team10.backend.domain.saving.type.SavingProductType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest(SavingProductController.class)
@WithMockUser
class SavingProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SavingProductService savingProductService;

    @Test
    @DisplayName("예금 상품 목록 조회 API는 예금 상품 목록을 반환한다")
    void getDepositProducts() throws Exception {
        SavingProductSummaryRes response = createSavingProductSummaryRes(1L, SavingProductType.DEPOSIT, "정기예금");

        when(savingProductService.getDepositProducts()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/savings/deposit-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("정기예금"))
                .andExpect(jsonPath("$[0].bankName").value("국민은행"))
                .andExpect(jsonPath("$[0].bankCode").value("KB"))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[0].interestRate").value(3.5))
                .andExpect(jsonPath("$[0].periodMonth").value(12))
                .andExpect(jsonPath("$[0].minAmount").value(100000L))
                .andExpect(jsonPath("$[0].maxAmount").value(10000000L))
                .andExpect(jsonPath("$[0].monthlyLimit").value(500000L))
                .andExpect(jsonPath("$[0].terms").value("가입 조건"));

        verify(savingProductService).getDepositProducts();
    }

    @Test
    @DisplayName("적금 상품 목록 조회 API는 적금 상품 목록을 반환한다")
    void getInstallmentProducts() throws Exception {
        SavingProductSummaryRes response = createSavingProductSummaryRes(2L, SavingProductType.INSTALLMENT, "청년 적금");

        when(savingProductService.getInstallmentProducts()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/savings/installment-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2L))
                .andExpect(jsonPath("$[0].name").value("청년 적금"))
                .andExpect(jsonPath("$[0].type").value("INSTALLMENT"));

        verify(savingProductService).getInstallmentProducts();
    }

    @Test
    @DisplayName("예금 상품 상세 조회 API는 예금 상품 상세를 반환한다")
    void getDepositProduct() throws Exception {
        SavingProductRes response = createSavingProductRes(1L, SavingProductType.DEPOSIT, "정기예금");

        when(savingProductService.getDepositProduct(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/savings/deposit-products/{productId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("정기예금"))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.terms").value("가입 조건"));

        verify(savingProductService).getDepositProduct(1L);
    }

    @Test
    @DisplayName("적금 상품 상세 조회 API는 적금 상품 상세를 반환한다")
    void getInstallmentProduct() throws Exception {
        SavingProductRes response = createSavingProductRes(2L, SavingProductType.INSTALLMENT, "청년 적금");

        when(savingProductService.getInstallmentProduct(2L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/savings/installment-products/{productId}", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.name").value("청년 적금"))
                .andExpect(jsonPath("$.type").value("INSTALLMENT"))
                .andExpect(jsonPath("$.monthlyLimit").value(500000L));

        verify(savingProductService).getInstallmentProduct(2L);
    }

    private SavingProductSummaryRes createSavingProductSummaryRes(
            Long id,
            SavingProductType type,
            String name
    ) {
        return new SavingProductSummaryRes(
                id,
                name,
                "국민은행",
                "KB",
                type,
                3.5,
                12,
                100000L,
                10000000L,
                500000L,
                "가입 조건"
        );
    }

    private SavingProductRes createSavingProductRes(Long id, SavingProductType type, String name) {
        return new SavingProductRes(
                id,
                name,
                "국민은행",
                "KB",
                type,
                3.5,
                12,
                100000L,
                10000000L,
                500000L,
                "가입 조건"
        );
    }
}
