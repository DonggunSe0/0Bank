package com.team10.backend.domain.account.controller;

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
import com.team10.backend.domain.account.dto.req.AccountCreateReq;
import com.team10.backend.domain.account.dto.req.AccountNicknameUpdateReq;
import com.team10.backend.domain.account.dto.req.AccountPasswordChangeReq;
import com.team10.backend.domain.account.dto.req.AccountPasswordSetReq;
import com.team10.backend.domain.account.dto.res.AccountCreateRes;
import com.team10.backend.domain.account.dto.res.AccountDetailRes;
import com.team10.backend.domain.account.dto.res.AccountPasswordRes;
import com.team10.backend.domain.account.dto.res.AccountSummaryRes;
import com.team10.backend.domain.account.service.AccountService;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
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

@WebMvcTest(AccountController.class)
@Import(AuthenticationPrincipalTestConfig.class)
@WithMockLongUser
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @MockitoBean
    private AccountService accountService;

    @Test
    @DisplayName("계좌 개설 API는 인증 사용자와 요청 본문을 받아 201을 반환한다")
    void createAccount() throws Exception {
        AccountCreateReq request = createAccountCreateReq("생활비 계좌", AccountType.DEPOSIT);
        AccountCreateRes response = new AccountCreateRes(
                1L,
                "100200300001",
                "생활비 계좌",
                AccountType.DEPOSIT,
                0L,
                AccountStatus.ACTIVE,
                LocalDateTime.of(2026, 6, 8, 15, 45)
        );

        when(accountService.createAccount(eq(1L), any(AccountCreateReq.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.accountNumber").value("100200300001"))
                .andExpect(jsonPath("$.nickname").value("생활비 계좌"))
                .andExpect(jsonPath("$.accountType").value("DEPOSIT"))
                .andExpect(jsonPath("$.balance").value(0L))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(accountService).createAccount(eq(1L), any(AccountCreateReq.class));
    }

    @Test
    @DisplayName("계좌 개설 API는 계좌 타입이 없으면 400을 반환한다")
    void createAccountWithoutAccountType() throws Exception {
        AccountCreateReq request = createAccountCreateReq("생활비 계좌", null);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }



    @Test
    @DisplayName("계좌 별칭 수정 API는 인증 사용자, accountId, 요청 본문을 받아 계좌 상세를 반환한다")
    void updateNickname() throws Exception {
        AccountNicknameUpdateReq request = new AccountNicknameUpdateReq("급여 계좌");
        AccountDetailRes response = new AccountDetailRes(
                1L,
                "100200300001",
                "급여 계좌",
                AccountType.DEPOSIT,
                0L,
                AccountStatus.ACTIVE,
                LocalDateTime.of(2026, 6, 8, 15, 45),
                LocalDateTime.of(2026, 6, 8, 16, 0)
        );

        when(accountService.updateNickname(eq(1L), eq(1L), any(AccountNicknameUpdateReq.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/accounts/{accountId}/nickname", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.nickname").value("급여 계좌"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(accountService).updateNickname(eq(1L), eq(1L), any(AccountNicknameUpdateReq.class));
    }


    @Test
    @DisplayName("계좌 비밀번호 설정 API는 인증 사용자와 accountId, 요청 본문을 받아 설정 결과를 반환한다")
    void setPassword() throws Exception {
        AccountPasswordSetReq request = new AccountPasswordSetReq("123456");
        AccountPasswordRes response = new AccountPasswordRes(1L, true);

        when(accountService.setPassword(eq(1L), eq(1L), any(AccountPasswordSetReq.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/password", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1L))
                .andExpect(jsonPath("$.passwordSet").value(true));

        verify(accountService).setPassword(eq(1L), eq(1L), any(AccountPasswordSetReq.class));
    }

    @Test
    @DisplayName("계좌 비밀번호 설정 API는 비밀번호가 숫자 6자리가 아니면 400을 반환한다")
    void setPasswordWithInvalidFormat() throws Exception {
        AccountPasswordSetReq request = new AccountPasswordSetReq("abc123");

        mockMvc.perform(post("/api/v1/accounts/{accountId}/password", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("계좌 비밀번호 변경 API는 현재 비밀번호와 새 비밀번호를 받아 변경 결과를 반환한다")
    void changePassword() throws Exception {
        AccountPasswordChangeReq request = new AccountPasswordChangeReq("123456", "654321");
        AccountPasswordRes response = new AccountPasswordRes(1L, true);

        when(accountService.changePassword(eq(1L), eq(1L), any(AccountPasswordChangeReq.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/accounts/{accountId}/password", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1L))
                .andExpect(jsonPath("$.passwordSet").value(true));

        verify(accountService).changePassword(eq(1L), eq(1L), any(AccountPasswordChangeReq.class));
    }

    @Test
    @DisplayName("계좌 비밀번호 변경 API는 새 비밀번호가 숫자 6자리가 아니면 400을 반환한다")
    void changePasswordWithInvalidNewPassword() throws Exception {
        AccountPasswordChangeReq request = new AccountPasswordChangeReq("123456", "abcdef");

        mockMvc.perform(patch("/api/v1/accounts/{accountId}/password", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("계좌 해지 API는 인증 사용자와 accountId를 받아 CLOSED 상태의 계좌 상세를 반환한다")
    void closeAccount() throws Exception {
        AccountDetailRes response = new AccountDetailRes(
                1L,
                "100200300001",
                "생활비 계좌",
                AccountType.DEPOSIT,
                0L,
                AccountStatus.CLOSED,
                LocalDateTime.of(2026, 6, 8, 15, 45),
                LocalDateTime.of(2026, 6, 8, 16, 0)
        );

        when(accountService.closeAccount(1L, 1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/{accountId}/close", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("CLOSED"));

        verify(accountService).closeAccount(1L, 1L);
    }

    @Test
    @DisplayName("내 계좌 목록 조회 API는 인증 사용자의 계좌 목록을 반환한다")
    void getAccounts() throws Exception {
        AccountSummaryRes response = new AccountSummaryRes(
                1L,
                "100200300001",
                "생활비 계좌",
                150000L,
                AccountStatus.ACTIVE
        );

        when(accountService.getAccounts(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].accountNumber").value("100200300001"))
                .andExpect(jsonPath("$[0].nickname").value("생활비 계좌"))
                .andExpect(jsonPath("$[0].balance").value(150000L))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(accountService).getAccounts(1L);
    }

    @Test
    @DisplayName("해지 계좌 목록 조회 API는 인증 사용자의 CLOSED 계좌 목록을 반환한다")
    void getClosedAccounts() throws Exception {
        AccountSummaryRes response = new AccountSummaryRes(
                1L,
                "100200300001",
                "생활비 계좌",
                0L,
                AccountStatus.CLOSED
        );

        when(accountService.getClosedAccounts(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/accounts/closed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].accountNumber").value("100200300001"))
                .andExpect(jsonPath("$[0].nickname").value("생활비 계좌"))
                .andExpect(jsonPath("$[0].status").value("CLOSED"));

        verify(accountService).getClosedAccounts(1L);
    }

    @Test
    @DisplayName("내 계좌 상세 조회 API는 인증 사용자와 accountId로 계좌 상세를 반환한다")
    void getAccount() throws Exception {
        AccountDetailRes response = new AccountDetailRes(
                1L,
                "100200300001",
                "생활비 계좌",
                AccountType.DEPOSIT,
                150000L,
                AccountStatus.ACTIVE,
                LocalDateTime.of(2026, 6, 8, 15, 45),
                LocalDateTime.of(2026, 6, 8, 16, 0)
        );

        when(accountService.getAccount(1L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/{accountId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.accountNumber").value("100200300001"))
                .andExpect(jsonPath("$.nickname").value("생활비 계좌"))
                .andExpect(jsonPath("$.accountType").value("DEPOSIT"))
                .andExpect(jsonPath("$.balance").value(150000L))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(accountService).getAccount(1L, 1L);
    }

    private AccountCreateReq createAccountCreateReq(String nickname, AccountType accountType) {
        return new AccountCreateReq(nickname, accountType);
    }
}
