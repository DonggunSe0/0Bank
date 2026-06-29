package com.team10.backend.domain.account.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLockServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountLockService accountLockService;

    @Test
    @DisplayName("두 계좌를 ID 작은 순서대로 락 조회하고 반환 순서는 입력 순서를 유지한다")
    void lockTwoAccounts() {
        Account firstAccount = createAccount(2L);
        Account secondAccount = createAccount(1L);

        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(secondAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(firstAccount));

        AccountLockService.LockedAccounts result = accountLockService.lockTwoAccounts(
                firstAccount,
                secondAccount
        );

        InOrder inOrder = inOrder(accountRepository);
        inOrder.verify(accountRepository).findByIdForUpdate(1L);
        inOrder.verify(accountRepository).findByIdForUpdate(2L);

        assertThat(result.firstAccount()).isSameAs(firstAccount);
        assertThat(result.secondAccount()).isSameAs(secondAccount);
    }

    @Test
    @DisplayName("락 대상 계좌가 null이면 예외가 발생한다")
    void lockTwoAccountsWithNullAccount() {
        Account account = createAccount(1L);

        assertThatThrownBy(() -> accountLockService.lockTwoAccounts(null, account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Accounts to lock must not be null");
    }

    @Test
    @DisplayName("락 대상 계좌 ID가 null이면 예외가 발생한다")
    void lockTwoAccountsWithNullAccountId() {
        Account accountWithoutId = createAccount(null);
        Account account = createAccount(1L);

        assertThatThrownBy(() -> accountLockService.lockTwoAccounts(accountWithoutId, account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account ID must not be null");
    }

    @Test
    @DisplayName("같은 계좌를 두 번 락 조회하려 하면 예외가 발생한다")
    void lockTwoAccountsWithSameAccount() {
        Account firstAccount = createAccount(1L);
        Account secondAccount = createAccount(1L);

        assertThatThrownBy(() -> accountLockService.lockTwoAccounts(firstAccount, secondAccount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot lock the same account twice: 1");
    }

    @Test
    @DisplayName("락 조회 대상 계좌가 없으면 ACCOUNT_NOT_FOUND 예외가 발생한다")
    void findByIdForUpdateNotFound() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountLockService.findByIdForUpdate(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    private Account createAccount(Long accountId) {
        User user = User.create(
                "test@example.com",
                "password",
                "테스터",
                "01012345678",
                LocalDate.of(1990, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", 1L);

        Account account = Account.create(user, "123456789012", "테스트 계좌", AccountType.DEPOSIT);
        ReflectionTestUtils.setField(account, "id", accountId);
        ReflectionTestUtils.setField(account, "status", AccountStatus.ACTIVE);
        return account;
    }
}
