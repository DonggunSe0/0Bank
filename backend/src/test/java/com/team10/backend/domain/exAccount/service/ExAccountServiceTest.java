package com.team10.backend.domain.exAccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.exAccount.Type.ExAccountTransactionDirection;
import com.team10.backend.domain.exAccount.Type.ExAccountType;
import com.team10.backend.domain.exAccount.dto.req.ExAccountLinkReq;
import com.team10.backend.domain.exAccount.dto.res.ExAccountDetailRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountRes;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import com.team10.backend.domain.exAccount.exception.ExAccountErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.exAccount.repository.ExAccountTransactionRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExAccountServiceTest {

    @Mock
    private ExAccountRepository accountRepository;

    @Mock
    private ExAccountTransactionRepository transactionRepository;

    @InjectMocks
    private ExAccountService exAccountService;

    private User user;

    @BeforeEach
    void setUp() {
        user = createUser(1L);
    }

    @Test
    @DisplayName("연동된 외부 계좌 목록을 조회한다")
    void getAccounts() {
        ExAccount account = createExAccount(10L);

        when(accountRepository.findAllByUserId(1L)).thenReturn(List.of(account));

        List<ExAccountRes> responses = exAccountService.getAccounts(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(10L);
        assertThat(responses.get(0).organization()).isEqualTo("국민은행");
        assertThat(responses.get(0).accountNoMasked()).isEqualTo("123456****1234");
    }

    @Test
    @DisplayName("외부 계좌 상세와 해당 계좌 거래내역을 함께 조회한다")
    void getAccountDetail() {
        ExAccount account = createExAccount(10L);
        ExAccountTransaction transaction = createTransaction(100L, account);

        when(accountRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findAllByExAccountIdAndExAccountUserIdOrderByTransactedAtDesc(10L, 1L))
                .thenReturn(List.of(transaction));

        ExAccountDetailRes response = exAccountService.getAccountDetail(1L, 10L);

        assertThat(response.account().id()).isEqualTo(10L);
        assertThat(response.account().accountName()).isEqualTo("KB Star 입출금통장");
        assertThat(response.transactions()).hasSize(1);
        assertThat(response.transactions().get(0).id()).isEqualTo(100L);
        assertThat(response.transactions().get(0).counterpartyName()).isEqualTo("스타벅스");
    }

    @Test
    @DisplayName("내 외부 계좌가 아니거나 존재하지 않으면 상세 조회에 실패한다")
    void getAccountDetailWithNotFoundAccount() {
        when(accountRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exAccountService.getAccountDetail(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExAccountErrorCode.EX_ACCOUNT_NOT_FOUND);
    }

    private ExAccount createExAccount(Long id) {
        ExAccountLinkReq request = new ExAccountLinkReq(
                "국민은행",
                "12345678901234",
                "KB Star 입출금통장",
                "생활비 통장",
                ExAccountType.DEMAND,
                BigDecimal.valueOf(1_500_000),
                BigDecimal.valueOf(1_200_000),
                LocalDate.of(2024, 1, 15),
                null,
                LocalDate.of(2026, 6, 18)
        );
        ExAccount account = request.toEntity(user);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private ExAccountTransaction createTransaction(Long id, ExAccount account) {
        ExAccountTransaction transaction = ExAccountTransaction.create(
                account,
                "KB-20260618143000-0001",
                LocalDateTime.of(2026, 6, 18, 14, 30),
                ExAccountTransactionDirection.OUT,
                BigDecimal.valueOf(45_000),
                BigDecimal.valueOf(1_455_000),
                "스타벅스",
                "카드 결제",
                "식비"
        );
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private User createUser(Long id) {
        User user = User.create(
                "user" + id + "@example.com",
                "password",
                "홍길동",
                "01012345678",
                LocalDate.of(1995, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
