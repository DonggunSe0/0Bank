package com.team10.backend.domain.investment.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountCloseReq;
import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountCreateReq;
import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountUpdateReq;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountCloseRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountCreateRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountDetailRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountOpenVerificationRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountSummaryRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountUpdateRes;
import com.team10.backend.domain.investment.account.service.InvestmentAccountService;
import com.team10.backend.domain.investment.account.type.InvestmentAccountStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import com.team10.backend.support.security.AuthenticationPrincipalTestConfig;
import com.team10.backend.support.security.WithMockLongUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvestmentAccountController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser
class InvestmentAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private InvestmentAccountService investmentAccountService;

    @Test
    @DisplayName("내 투자 계좌 목록 조회 API는 인증 사용자의 해지되지 않은 투자 계좌 목록을 반환한다")
    void getAccounts() throws Exception {
        InvestmentAccountSummaryRes response = new InvestmentAccountSummaryRes(
                "1234567890-12",
                "모의투자 계좌",
                10000L,
                CurrencyCode.KRW,
                InvestmentAccountStatus.ACTIVE
        );

        when(investmentAccountService.getAccounts(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/investment/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value("1234567890-12"))
                .andExpect(jsonPath("$[0].nickname").value("모의투자 계좌"))
                .andExpect(jsonPath("$[0].cashBalance").value(10000L))
                .andExpect(jsonPath("$[0].currencyCode").value("KRW"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(investmentAccountService).getAccounts(1L);
    }

    @Test
    @DisplayName("내 투자 계좌 상세 조회 API는 인증 사용자와 accountId로 투자 계좌 상세를 반환한다")
    void getAccount() throws Exception {
        InvestmentAccountDetailRes response = new InvestmentAccountDetailRes(
                "1234567890-12",
                "모의투자 계좌",
                10000L,
                CurrencyCode.KRW,
                InvestmentAccountStatus.ACTIVE,
                LocalDateTime.of(2026, 6, 17, 10, 30)
        );

        when(investmentAccountService.getAccount(1L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/investment/accounts/{accountId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("1234567890-12"))
                .andExpect(jsonPath("$.nickname").value("모의투자 계좌"))
                .andExpect(jsonPath("$.cashBalance").value(10000L))
                .andExpect(jsonPath("$.currencyCode").value("KRW"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(investmentAccountService).getAccount(1L, 1L);
    }

    @Test
    @DisplayName("투자 계좌 개설 인증키 발급 API는 인증 사용자를 받아 200을 반환한다")
    void issueOpenVerificationKey() throws Exception {
        InvestmentAccountOpenVerificationRes response = new InvestmentAccountOpenVerificationRes(
                "verification-key",
                600L
        );

        when(investmentAccountService.issueOpenVerificationKey(1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/investment/accounts/open-verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationKey").value("verification-key"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600L));

        verify(investmentAccountService).issueOpenVerificationKey(1L);
    }

    @Test
    @DisplayName("투자 계좌 개설 API는 인증 사용자와 요청 본문을 받아 201을 반환한다")
    void createAccount() throws Exception {
        InvestmentAccountCreateReq request =
                new InvestmentAccountCreateReq("모의투자 계좌", "123456", "verification-key", CurrencyCode.KRW);
        InvestmentAccountCreateRes response = new InvestmentAccountCreateRes(
                "1234567890-12",
                "모의투자 계좌",
                0L,
                CurrencyCode.KRW,
                InvestmentAccountStatus.ACTIVE,
                LocalDateTime.of(2026, 6, 17, 10, 30)
        );

        when(investmentAccountService.createAccount(eq(1L), any(InvestmentAccountCreateReq.class))).thenReturn(
                response);

        mockMvc.perform(post("/api/v1/investment/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("1234567890-12"))
                .andExpect(jsonPath("$.nickname").value("모의투자 계좌"))
                .andExpect(jsonPath("$.cashBalance").value(0L))
                .andExpect(jsonPath("$.currencyCode").value("KRW"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(investmentAccountService).createAccount(eq(1L), any(InvestmentAccountCreateReq.class));
    }

    @Test
    @DisplayName("투자 계좌 개설 API는 비밀번호가 숫자 6자리가 아니면 400을 반환한다")
    void createAccountWithInvalidPassword() throws Exception {
        InvestmentAccountCreateReq request =
                new InvestmentAccountCreateReq("모의투자 계좌", "12345a", "verification-key", CurrencyCode.KRW);

        mockMvc.perform(post("/api/v1/investment/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("투자 계좌 개설 API는 개설 인증키가 없으면 400을 반환한다")
    void createAccountWithoutVerificationKey() throws Exception {
        InvestmentAccountCreateReq request =
                new InvestmentAccountCreateReq("모의투자 계좌", "123456", null, CurrencyCode.KRW);

        mockMvc.perform(post("/api/v1/investment/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("투자 계좌 개설 API는 화폐 종류가 없으면 400을 반환한다.")
    void createAccountWithInvalidCurrencyCode() throws Exception {
        InvestmentAccountCreateReq request =
                new InvestmentAccountCreateReq("모의투자 계좌", "123456", "verification-key", null);

        mockMvc.perform(post("/api/v1/investment/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("투자 계좌 정보 수정 API는 인증 사용자, accountId, 요청 본문을 받아 계좌 상세를 반환한다")
    void updateAccount() throws Exception {
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", "장기투자 계좌", "654321");
        InvestmentAccountUpdateRes response = new InvestmentAccountUpdateRes(
                "장기투자 계좌",
                LocalDateTime.of(2026, 6, 17, 11, 0)
        );

        when(investmentAccountService.updateAccount(eq(1L), eq(1L), any(InvestmentAccountUpdateReq.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/investment/accounts/{accountId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("장기투자 계좌"))
                .andExpect(jsonPath("$.updatedAt").value("2026-06-17T11:00:00"));

        verify(investmentAccountService).updateAccount(eq(1L), eq(1L), any(InvestmentAccountUpdateReq.class));
    }

    @Test
    @DisplayName("투자 계좌 정보 수정 API는 별칭만 전달해도 200을 반환한다")
    void updateAccountWithNicknameOnly() throws Exception {
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", "장기투자 계좌", null);
        InvestmentAccountUpdateRes response = new InvestmentAccountUpdateRes(
                "장기투자 계좌",
                LocalDateTime.of(2026, 6, 17, 11, 0)
        );

        when(investmentAccountService.updateAccount(eq(1L), eq(1L), any(InvestmentAccountUpdateReq.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/investment/accounts/{accountId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("장기투자 계좌"));
    }

    @Test
    @DisplayName("투자 계좌 정보 수정 API는 새 비밀번호만 전달해도 200을 반환한다")
    void updateAccountWithPasswordOnly() throws Exception {
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", null, "654321");
        InvestmentAccountUpdateRes response = new InvestmentAccountUpdateRes(
                "모의투자 계좌",
                LocalDateTime.of(2026, 6, 17, 11, 0)
        );

        when(investmentAccountService.updateAccount(eq(1L), eq(1L), any(InvestmentAccountUpdateReq.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/investment/accounts/{accountId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("모의투자 계좌"));
    }

    @Test
    @DisplayName("투자 계좌 정보 수정 API는 비밀번호가 숫자 6자리가 아니면 400을 반환한다")
    void updateAccountWithInvalidPassword() throws Exception {
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("12345a", "장기투자 계좌", null);

        mockMvc.perform(patch("/api/v1/investment/accounts/{accountId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("투자 계좌 정보 수정 API는 별칭이 공백이면 400을 반환한다")
    void updateAccountWithBlankNickname() throws Exception {
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", "", null);

        mockMvc.perform(patch("/api/v1/investment/accounts/{accountId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("투자 계좌 정보 수정 API는 새 비밀번호가 숫자 6자리가 아니면 400을 반환한다")
    void updateAccountWithInvalidNewPassword() throws Exception {
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", null, "65432a");

        mockMvc.perform(patch("/api/v1/investment/accounts/{accountId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("투자 계좌 정보 수정 API는 수정할 값이 없으면 400을 반환한다")
    void updateAccountWithoutUpdateValue() throws Exception {
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", null, null);

        mockMvc.perform(patch("/api/v1/investment/accounts/{accountId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("투자 계좌 해지 API는 인증 사용자, accountId, 요청 본문을 받아 CLOSED 상태를 반환한다")
    void closeAccount() throws Exception {
        InvestmentAccountCloseReq request = new InvestmentAccountCloseReq("123456");
        InvestmentAccountCloseRes response = new InvestmentAccountCloseRes(
                InvestmentAccountStatus.CLOSED,
                LocalDateTime.of(2026, 6, 17, 11, 0)
        );

        when(investmentAccountService.closeAccount(eq(1L), eq(1L), any(InvestmentAccountCloseReq.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/investment/accounts/{accountId}/close", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.updatedAt").value("2026-06-17T11:00:00"));

        verify(investmentAccountService).closeAccount(eq(1L), eq(1L), any(InvestmentAccountCloseReq.class));
    }

    @Test
    @DisplayName("투자 계좌 해지 API는 비밀번호가 숫자 6자리가 아니면 400을 반환한다")
    void closeAccountWithInvalidPassword() throws Exception {
        InvestmentAccountCloseReq request = new InvestmentAccountCloseReq("12345a");

        mockMvc.perform(post("/api/v1/investment/accounts/{accountId}/close", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
