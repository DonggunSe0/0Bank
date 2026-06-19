package com.team10.backend.domain.exAccount.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team10.backend.domain.exAccount.Type.ExAccountStatus;
import com.team10.backend.domain.exAccount.Type.ExAccountTransactionDirection;
import com.team10.backend.domain.exAccount.Type.ExAccountType;
import com.team10.backend.domain.exAccount.dto.req.ExAccountLinkReq;
import com.team10.backend.domain.exAccount.dto.req.ExAccountTransactionSyncReq;
import com.team10.backend.domain.exAccount.dto.res.ExAccountCandidateRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountDetailRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRefreshRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRes;
import com.team10.backend.domain.exAccount.service.ExAccountService;
import com.team10.backend.domain.exAccount.service.ExAccountSyncService;
import com.team10.backend.domain.exAccount.service.ExAccountTransactionService;
import com.team10.backend.support.security.AuthenticationPrincipalTestConfig;
import com.team10.backend.support.security.WithMockLongUser;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(ExAccountController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser
class ExAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExAccountService exAccountService;

    @MockitoBean
    private ExAccountSyncService exAccountSyncService;

    @MockitoBean
    private ExAccountTransactionService exAccountTransactionService;

    @Test
    @DisplayName("연동된 외부 계좌 목록 조회 API는 인증 사용자의 외부 계좌 목록을 반환한다")
    void getAccounts() throws Exception {
        ExAccountRes response = createAccountRes();

        when(exAccountService.getAccounts(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/external-accounts/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].organization").value("국민은행"))
                .andExpect(jsonPath("$[0].accountNoMasked").value("123456****1234"))
                .andExpect(jsonPath("$[0].accountName").value("KB Star 입출금통장"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(exAccountService).getAccounts(1L);
    }

    @Test
    @DisplayName("외부 계좌 상세 조회 API는 계좌 상세와 거래내역을 함께 반환한다")
    void getAccountDetail() throws Exception {
        ExAccountDetailRes response = ExAccountDetailRes.of(
                createAccountRes(),
                List.of(createTransactionRes())
        );

        when(exAccountService.getAccountDetail(1L, 10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/external-accounts/accounts/{exAccountId}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(10L))
                .andExpect(jsonPath("$.account.accountName").value("KB Star 입출금통장"))
                .andExpect(jsonPath("$.transactions[0].id").value(100L))
                .andExpect(jsonPath("$.transactions[0].direction").value("OUT"))
                .andExpect(jsonPath("$.transactions[0].counterpartyName").value("스타벅스"));

        verify(exAccountService).getAccountDetail(1L, 10L);
    }

    @Test
    @DisplayName("외부 계좌 후보 조회 API는 조회 결과를 저장하지 않고 후보 목록을 반환한다")
    void getLinkCandidates() throws Exception {
        ExAccountCandidateRes response = new ExAccountCandidateRes(
                "국민은행",
                "123456****1234",
                "KB Star 입출금통장",
                "생활비 통장",
                ExAccountType.DEMAND,
                BigDecimal.valueOf(1_500_000),
                BigDecimal.valueOf(1_200_000),
                LocalDate.of(2024, 1, 15),
                null,
                LocalDate.of(2026, 6, 18),
                false
        );

        when(exAccountSyncService.getLinkCandidates(eq(1L), any())).thenReturn(List.of(response));

        mockMvc.perform(post("/api/v1/external-accounts/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCandidateRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organization").value("국민은행"))
                .andExpect(jsonPath("$[0].accountNoMasked").value("123456****1234"))
                .andExpect(jsonPath("$[0].linked").value(false));

        verify(exAccountSyncService).getLinkCandidates(eq(1L), any());
    }

    @Test
    @DisplayName("외부 계좌 후보 조회 API는 계좌 목록이 비어 있으면 400을 반환한다")
    void getLinkCandidatesWithEmptyAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/external-accounts/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accounts\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("외부 계좌 연동 API는 인증 사용자와 선택 계좌 요청을 받아 201을 반환한다")
    void linkAccount() throws Exception {
        ExAccountRes response = createAccountRes();

        when(exAccountSyncService.linkAccount(eq(1L), any(ExAccountLinkReq.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/external-accounts/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLinkRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.organization").value("국민은행"))
                .andExpect(jsonPath("$.accountName").value("KB Star 입출금통장"));

        verify(exAccountSyncService).linkAccount(eq(1L), any(ExAccountLinkReq.class));
    }

    @Test
    @DisplayName("외부 계좌 연동 API는 필수값이 없으면 400을 반환한다")
    void linkAccountWithoutAccountNumber() throws Exception {
        mockMvc.perform(post("/api/v1/external-accounts/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organization": "국민은행",
                                  "accountNumber": "",
                                  "accountName": "KB Star 입출금통장",
                                  "assetType": "DEMAND"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("거래내역 새로고침 API는 거래내역 저장/갱신 결과와 상세 응답을 반환한다")
    void refreshTransactions() throws Exception {
        ExAccountDetailRes detail = ExAccountDetailRes.of(
                createAccountRes(),
                List.of(createTransactionRes())
        );
        ExAccountTransactionRefreshRes response = ExAccountTransactionRefreshRes.of(1, 1, 0, detail);

        when(exAccountTransactionService.refreshTransactions(eq(1L), eq(10L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/external-accounts/accounts/{exAccountId}/transactions/refresh", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTransactionRefreshRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedCount").value(1))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.detail.account.id").value(10L))
                .andExpect(jsonPath("$.detail.transactions[0].id").value(100L));

        verify(exAccountTransactionService).refreshTransactions(eq(1L), eq(10L), any());
    }

    @Test
    @DisplayName("거래내역 새로고침 API는 거래내역 목록이 비어 있으면 400을 반환한다")
    void refreshTransactionsWithEmptyTransactions() throws Exception {
        mockMvc.perform(post("/api/v1/external-accounts/accounts/{exAccountId}/transactions/refresh", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactions\":[]}"))
                .andExpect(status().isBadRequest());
    }

    private ExAccountRes createAccountRes() {
        return new ExAccountRes(
                10L,
                "국민은행",
                "123456****1234",
                "KB Star 입출금통장",
                "생활비 통장",
                ExAccountType.DEMAND,
                BigDecimal.valueOf(1_500_000),
                BigDecimal.valueOf(1_200_000),
                LocalDate.of(2024, 1, 15),
                null,
                LocalDate.of(2026, 6, 18),
                ExAccountStatus.ACTIVE
        );
    }

    private ExAccountTransactionRes createTransactionRes() {
        return new ExAccountTransactionRes(
                100L,
                10L,
                LocalDateTime.of(2026, 6, 18, 14, 30),
                ExAccountTransactionDirection.OUT,
                BigDecimal.valueOf(45_000),
                BigDecimal.valueOf(1_455_000),
                "스타벅스",
                "카드 결제",
                "식비"
        );
    }

    private String createCandidateRequestJson() {
        return """
                {
                  "accounts": [
                    {
                      "organization": "국민은행",
                      "accountNumber": "12345678901234",
                      "accountName": "KB Star 입출금통장",
                      "accountAlias": "생활비 통장",
                      "assetType": "DEMAND",
                      "balance": 1500000.00,
                      "withdrawableAmount": 1200000.00,
                      "openedAt": "2024-01-15",
                      "lastTransactionAt": "2026-06-18"
                    }
                  ]
                }
                """;
    }

    private String createLinkRequestJson() {
        return """
                {
                  "organization": "국민은행",
                  "accountNumber": "12345678901234",
                  "accountName": "KB Star 입출금통장",
                  "accountAlias": "생활비 통장",
                  "assetType": "DEMAND",
                  "balance": 1500000.00,
                  "withdrawableAmount": 1200000.00,
                  "openedAt": "2024-01-15",
                  "lastTransactionAt": "2026-06-18"
                }
                """;
    }

    private String createTransactionRefreshRequestJson() {
        return """
                {
                  "transactions": [
                    {
                      "transactionKey": "KB-20260618143000-0001",
                      "transactedAt": "2026-06-18T14:30:00",
                      "direction": "OUT",
                      "amount": 45000.00,
                      "balanceAfter": 1455000.00,
                      "counterpartyName": "스타벅스",
                      "memo": "카드 결제",
                      "rawCategory": "식비"
                    }
                  ]
                }
                """;
    }
}
