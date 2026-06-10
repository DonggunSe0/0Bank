package com.team10.backend.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.account.dto.req.AccountCreateReq;
import com.team10.backend.domain.account.dto.res.AccountRes;
import com.team10.backend.domain.account.dto.res.AccountSummaryRes;
import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Constructor;
import java.time.LocalDate;
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
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AccountService accountService;

    private User verifiedUser;
    private User unverifiedUser;

    @BeforeEach
    void setUp() {
        verifiedUser = createUser(1L, true);
        unverifiedUser = createUser(2L, false);
    }

    @Test
    @DisplayName("본인인증 완료 사용자는 계좌를 개설할 수 있다")
    void createAccount() {
        AccountCreateReq request = createAccountCreateReq("생활비 계좌", AccountType.DEPOSIT);

        when(entityManager.find(User.class, 1L)).thenReturn(verifiedUser);
        when(accountRepository.existsByAccountNumber(any(String.class))).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 1L);
            return account;
        });

        AccountRes response = accountService.createAccount(1L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("생활비 계좌");
        assertThat(response.accountType()).isEqualTo(AccountType.DEPOSIT);
        assertThat(response.balance()).isZero();
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.accountNumber()).hasSize(12);

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 계좌를 개설할 수 없다")
    void createAccountWithNotFoundUser() {
        AccountCreateReq request = createAccountCreateReq("생활비 계좌", AccountType.DEPOSIT);

        when(entityManager.find(User.class, 999L)).thenReturn(null);

        assertThatThrownBy(() -> accountService.createAccount(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("본인인증 미완료 사용자는 계좌를 개설할 수 없다")
    void createAccountWithoutIdentityVerification() {
        AccountCreateReq request = createAccountCreateReq("생활비 계좌", AccountType.DEPOSIT);

        when(entityManager.find(User.class, 2L)).thenReturn(unverifiedUser);

        assertThatThrownBy(() -> accountService.createAccount(2L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.IDENTITY_VERIFICATION_REQUIRED);
    }

    @Test
    @DisplayName("사용자 ID로 내 계좌 목록을 조회한다")
    void getAccounts() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");

        when(accountRepository.findAllByUserId(1L)).thenReturn(List.of(account));

        List<AccountSummaryRes> responses = accountService.getAccounts(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).accountNumber()).isEqualTo("100200300001");
        assertThat(responses.get(0).nickname()).isEqualTo("생활비 계좌");
        assertThat(responses.get(0).balance()).isZero();
        assertThat(responses.get(0).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("사용자 ID와 계좌 ID로 내 계좌 상세를 조회한다")
    void getAccount() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));

        AccountRes response = accountService.getAccount(1L, 1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.accountNumber()).isEqualTo("100200300001");
        assertThat(response.nickname()).isEqualTo("생활비 계좌");
        assertThat(response.accountType()).isEqualTo(AccountType.DEPOSIT);
        assertThat(response.balance()).isZero();
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("내 계좌가 아니거나 존재하지 않는 계좌는 상세 조회에 실패한다")
    void getAccountWithNotFoundAccount() {
        when(accountRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    private AccountCreateReq createAccountCreateReq(String nickname, AccountType accountType) {
        return new AccountCreateReq(nickname, accountType);
    }

    private User createUser(Long id, Boolean identityVerified) {
        User user = instantiateUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "email", "user" + id + "@example.com");
        ReflectionTestUtils.setField(user, "password", "password");
        ReflectionTestUtils.setField(user, "name", "홍길동");
        ReflectionTestUtils.setField(user, "phoneNumber", "01012345678");
        ReflectionTestUtils.setField(user, "birthDate", LocalDate.of(1995, 1, 1));
        ReflectionTestUtils.setField(user, "identityVerified", identityVerified);
        return user;
    }

    private User instantiateUser() {
        try {
            Constructor<User> constructor = User.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("User 테스트 객체 생성에 실패했습니다.", e);
        }
    }

    private Account createAccount(Long id, User user, String accountNumber, String nickname) {
        Account account = Account.create(user, accountNumber, nickname, AccountType.DEPOSIT);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
