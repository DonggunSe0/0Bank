package com.team10.backend.domain.saving.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.saving.dto.req.DepositCreateReq;
import com.team10.backend.domain.saving.dto.req.EarlyCancelReq;
import com.team10.backend.domain.saving.dto.req.InstallmentCreateReq;
import com.team10.backend.domain.saving.dto.req.MaturityReq;
import com.team10.backend.domain.saving.dto.res.*;
import com.team10.backend.domain.saving.service.SavingDepositService;
import com.team10.backend.domain.saving.type.DepositStatus;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import com.team10.backend.domain.saving.type.SavingProductType;
import com.team10.backend.support.security.AuthenticationPrincipalTestConfig;
import com.team10.backend.support.security.WithMockLongUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SavingDepositController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser
class SavingDepositControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private SavingDepositService savingDepositService;

    @Test
    @DisplayName("예금 가입 API는 인증 사용자와 요청 본문을 받아 201을 반환한다")
    void createDeposit() throws Exception {
        DepositCreateReq request = new DepositCreateReq(1L, 1L, 1000000L);
        DepositCreateRes response = new DepositCreateRes(
                1L,
                DepositStatus.ACTIVE,
                1000000L,
                LocalDate.of(2027, 6, 17),
                35000L
        );

        when(savingDepositService.createDeposit(eq(1L), any(DepositCreateReq.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/savings/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.depositId").value(1L))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.principal").value(1000000L))
                .andExpect(jsonPath("$.maturityDate").value("2027-06-17"))
                .andExpect(jsonPath("$.expectedInterest").value(35000L));

        verify(savingDepositService).createDeposit(eq(1L), any(DepositCreateReq.class));
    }

    @Test
    @DisplayName("내 예금 목록 조회 API는 인증 사용자의 예금 목록을 반환한다")
    void getDeposits() throws Exception {
        DepositSummaryRes response = createDepositSummaryRes(1L, DepositStatus.ACTIVE);

        when(savingDepositService.getDeposits(1L, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/savings/deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].depositId").value(1L))
                .andExpect(jsonPath("$[0].productName").value("정기예금"))
                .andExpect(jsonPath("$[0].bankName").value("국민은행"))
                .andExpect(jsonPath("$[0].principal").value(1000000L))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(savingDepositService).getDeposits(1L, null);
    }

    @Test
    @DisplayName("내 예금 목록 조회 API는 상태값으로 필터링할 수 있다")
    void getDepositsWithStatus() throws Exception {
        DepositSummaryRes response = createDepositSummaryRes(1L, DepositStatus.MATURED);

        when(savingDepositService.getDeposits(1L, DepositStatus.MATURED)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/savings/deposits")
                        .param("status", "MATURED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].depositId").value(1L))
                .andExpect(jsonPath("$[0].status").value("MATURED"));

        verify(savingDepositService).getDeposits(1L, DepositStatus.MATURED);
    }


    @Test
    @DisplayName("내 예금 상세 조회 API는 인증 사용자의 예금 상세를 반환한다")
    void getDeposit() throws Exception {
        DepositDetailRes response = createDepositDetailRes(1L);

        when(savingDepositService.getDeposit(1L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/savings/deposits/{depositId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositId").value(1L))
                .andExpect(jsonPath("$.productName").value("정기예금"))
                .andExpect(jsonPath("$.bankName").value("국민은행"))
                .andExpect(jsonPath("$.principal").value(1000000L))
                .andExpect(jsonPath("$.interestRate").value(3.5))
                .andExpect(jsonPath("$.expectedInterest").value(35000L))
                .andExpect(jsonPath("$.maturityDate").value("2027-06-17"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(savingDepositService).getDeposit(1L, 1L);
    }

    @Test
    @DisplayName("예금 가입 API는 필수값이 없으면 400을 반환한다")
    void createDepositWithoutRequiredValue() throws Exception {
        DepositCreateReq request = new DepositCreateReq(null, 1L, 1000000L);

        mockMvc.perform(post("/api/v1/savings/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("적금 가입 API는 인증 사용자와 요청 본문을 받아 201을 반환한다")
    void createInstallment() throws Exception {
        InstallmentCreateReq request = new InstallmentCreateReq(
                2L,
                1L,
                100000L,
                1200000L,
                true
        );
        InstallmentCreateRes response = new InstallmentCreateRes(
                1L,
                InstallmentStatus.ACTIVE,
                LocalDate.of(2027, 6, 17),
                8L
        );

        when(savingDepositService.createInstallment(eq(1L), any(InstallmentCreateReq.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/savings/installments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.installmentId").value(1L))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.maturityDate").value("2027-06-17"))
                .andExpect(jsonPath("$.progressRate").value(8L));

        verify(savingDepositService).createInstallment(eq(1L), any(InstallmentCreateReq.class));
    }

    @Test
    @DisplayName("내 적금 목록 조회 API는 인증 사용자의 적금 목록을 반환한다")
    void getInstallments() throws Exception {
        InstallmentSummaryRes response = createInstallmentSummaryRes(1L, InstallmentStatus.ACTIVE);

        when(savingDepositService.getInstallments(1L, null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/savings/installments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].installmentId").value(1L))
                .andExpect(jsonPath("$[0].productName").value("정기적금"))
                .andExpect(jsonPath("$[0].bankName").value("국민은행"))
                .andExpect(jsonPath("$[0].paidAmount").value(100000L))
                .andExpect(jsonPath("$[0].progressRate").value(8L))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(savingDepositService).getInstallments(1L, null);
    }

    @Test
    @DisplayName("내 적금 목록 조회 API는 상태값으로 필터링할 수 있다")
    void getInstallmentsWithStatus() throws Exception {
        InstallmentSummaryRes response = createInstallmentSummaryRes(1L, InstallmentStatus.MATURED);

        when(savingDepositService.getInstallments(1L, InstallmentStatus.MATURED))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/savings/installments")
                        .param("status", "MATURED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].installmentId").value(1L))
                .andExpect(jsonPath("$[0].status").value("MATURED"));

        verify(savingDepositService).getInstallments(1L, InstallmentStatus.MATURED);
    }

    @Test
    @DisplayName("내 적금 상세 조회 API는 인증 사용자의 적금 상세를 반환한다")
    void getInstallment() throws Exception {
        InstallmentDetailRes response = createInstallmentDetailRes(1L);

        when(savingDepositService.getInstallment(1L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/savings/installments/{installmentId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installmentId").value(1L))
                .andExpect(jsonPath("$.productName").value("정기적금"))
                .andExpect(jsonPath("$.bankName").value("국민은행"))
                .andExpect(jsonPath("$.monthlyAmount").value(100000L))
                .andExpect(jsonPath("$.paidAmount").value(100000L))
                .andExpect(jsonPath("$.targetAmount").value(1200000L))
                .andExpect(jsonPath("$.progressRate").value(8L))
                .andExpect(jsonPath("$.maturityDate").value("2027-06-17"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(savingDepositService).getInstallment(1L, 1L);
    }

    @Test
    @DisplayName("예상 이자 조회 API는 예금 또는 적금의 예상 이자를 반환한다")
    void getInterestPreview() throws Exception {
        InterestPreviewRes response = new InterestPreviewRes(
                1L,
                SavingProductType.INSTALLMENT,
                1200000L,
                3.0,
                19500L,
                1219500L
        );

        when(savingDepositService.getInterestPreview(1L, 1L, SavingProductType.INSTALLMENT))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/savings/{savingId}/interest-preview", 1L)
                        .param("savingType", "INSTALLMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingId").value(1L))
                .andExpect(jsonPath("$.savingType").value("INSTALLMENT"))
                .andExpect(jsonPath("$.principal").value(1200000L))
                .andExpect(jsonPath("$.interestRate").value(3.0))
                .andExpect(jsonPath("$.expectedInterest").value(19500L))
                .andExpect(jsonPath("$.expectedTotalAmount").value(1219500L));

        verify(savingDepositService)
                .getInterestPreview(1L, 1L, SavingProductType.INSTALLMENT);
    }



    @Test
    @DisplayName("중도 해지 API는 인증 사용자의 예금 또는 적금을 해지한다")
    void cancelSaving() throws Exception {
        EarlyCancelReq request = new EarlyCancelReq(SavingProductType.DEPOSIT);
        EarlyCancelRes response = new EarlyCancelRes(
                1L,
                SavingProductType.DEPOSIT,
                1000000L,
                17500L,
                1017500L,
                "CANCELLED"
        );

        when(savingDepositService.cancelSaving(eq(1L), eq(1L), any(EarlyCancelReq.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/savings/{savingId}/cancel", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingId").value(1L))
                .andExpect(jsonPath("$.savingType").value("DEPOSIT"))
                .andExpect(jsonPath("$.principalAmount").value(1000000L))
                .andExpect(jsonPath("$.interestAmount").value(17500L))
                .andExpect(jsonPath("$.refundAmount").value(1017500L))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(savingDepositService)
                .cancelSaving(eq(1L), eq(1L), any(EarlyCancelReq.class));
    }

    @Test
    @DisplayName("중도 해지 API는 필수값이 없으면 400을 반환한다")
    void cancelSavingWithoutRequiredValue() throws Exception {
        EarlyCancelReq request = new EarlyCancelReq(null);

        mockMvc.perform(post("/api/v1/savings/{savingId}/cancel", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("만기 처리 API는 인증 사용자의 예금 또는 적금을 만기 처리한다")
    void matureSaving() throws Exception {
        MaturityReq request = new MaturityReq(SavingProductType.DEPOSIT);
        MaturityRes response = new MaturityRes(
                1L,
                SavingProductType.DEPOSIT,
                1000000L,
                35000L,
                1035000L,
                "MATURED"
        );

        when(savingDepositService.matureSaving(eq(1L), eq(1L), any(MaturityReq.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/savings/{savingId}/maturity", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingId").value(1L))
                .andExpect(jsonPath("$.savingType").value("DEPOSIT"))
                .andExpect(jsonPath("$.principalAmount").value(1000000L))
                .andExpect(jsonPath("$.interestAmount").value(35000L))
                .andExpect(jsonPath("$.payoutAmount").value(1035000L))
                .andExpect(jsonPath("$.status").value("MATURED"));

        verify(savingDepositService)
                .matureSaving(eq(1L), eq(1L), any(MaturityReq.class));
    }

    @Test
    @DisplayName("만기 처리 API는 필수값이 없으면 400을 반환한다")
    void matureSavingWithoutRequiredValue() throws Exception {
        MaturityReq request = new MaturityReq(null);

        mockMvc.perform(post("/api/v1/savings/{savingId}/maturity", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("적금 가입 API는 필수값이 없으면 400을 반환한다")
    void createInstallmentWithoutRequiredValue() throws Exception {
        InstallmentCreateReq request = new InstallmentCreateReq(
                null,
                1L,
                100000L,
                1200000L,
                true
        );

        mockMvc.perform(post("/api/v1/savings/installments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private DepositSummaryRes createDepositSummaryRes(Long depositId, DepositStatus status) {
        return new DepositSummaryRes(
                depositId,
                "정기예금",
                "국민은행",
                1000000L,
                status
        );
    }

    private DepositDetailRes createDepositDetailRes(Long depositId) {
        return new DepositDetailRes(
                depositId,
                "정기예금",
                "국민은행",
                1000000L,
                3.5,
                35000L,
                LocalDate.of(2027, 6, 17),
                DepositStatus.ACTIVE
        );
    }

    private InstallmentSummaryRes createInstallmentSummaryRes(
            Long installmentId,
            InstallmentStatus status
    ) {
        return new InstallmentSummaryRes(
                installmentId,
                "정기적금",
                "국민은행",
                100000L,
                8L,
                status
        );
    }

    private InstallmentDetailRes createInstallmentDetailRes(Long installmentId) {
        return new InstallmentDetailRes(
                installmentId,
                "정기적금",
                "국민은행",
                100000L,
                100000L,
                1200000L,
                8L,
                LocalDate.of(2027, 6, 17),
                InstallmentStatus.ACTIVE
        );
    }

}
