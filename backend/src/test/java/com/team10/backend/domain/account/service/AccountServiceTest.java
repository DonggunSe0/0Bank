package com.team10.backend.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.account.dto.req.AccountCreateReq;
import com.team10.backend.domain.account.dto.req.AccountNicknameUpdateReq;
import com.team10.backend.domain.account.dto.req.AccountPasswordChangeReq;
import com.team10.backend.domain.account.dto.req.AccountPasswordSetReq;
import com.team10.backend.domain.account.dto.res.AccountCreateRes;
import com.team10.backend.domain.account.dto.res.AccountDetailRes;
import com.team10.backend.domain.account.dto.res.AccountPasswordRes;
import com.team10.backend.domain.account.dto.res.AccountSummaryRes;
import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

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

        when(userRepository.findById(1L)).thenReturn(Optional.of(verifiedUser));
        when(accountRepository.existsByAccountNumber(any(String.class))).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 1L);
            return account;
        });

        AccountCreateRes response = accountService.createAccount(1L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("생활비 계좌");
        assertThat(response.accountType()).isEqualTo(AccountType.DEPOSIT);
        assertThat(response.balance()).isZero();
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.accountNumber()).hasSize(12);

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("일반 계좌 개설 API에서는 예금 계좌 타입을 생성할 수 없다")
    void createAccountWithSavingDepositType() {
        AccountCreateReq request = createAccountCreateReq("예금 계좌", AccountType.SAVING_DEPOSIT);

        assertThatThrownBy(() -> accountService.createAccount(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_TYPE);

        verify(userRepository, never()).findById(any(Long.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("일반 계좌 개설 API에서는 적금 계좌 타입을 생성할 수 없다")
    void createAccountWithSavingInstallmentType() {
        AccountCreateReq request = createAccountCreateReq("적금 계좌", AccountType.SAVING_INSTALLMENT);

        assertThatThrownBy(() -> accountService.createAccount(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_TYPE);

        verify(userRepository, never()).findById(any(Long.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 계좌를 개설할 수 없다")
    void createAccountWithNotFoundUser() {
        AccountCreateReq request = createAccountCreateReq("생활비 계좌", AccountType.DEPOSIT);

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.createAccount(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("본인인증 미완료 사용자는 계좌를 개설할 수 없다")
    void createAccountWithoutIdentityVerification() {
        AccountCreateReq request = createAccountCreateReq("생활비 계좌", AccountType.DEPOSIT);

        when(userRepository.findById(2L)).thenReturn(Optional.of(unverifiedUser));

        assertThatThrownBy(() -> accountService.createAccount(2L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.IDENTITY_VERIFICATION_REQUIRED);
    }

    @Test
    @DisplayName("계좌번호 생성 최대 시도 횟수를 초과하면 계좌 개설에 실패한다")
    void createAccountWithAccountNumberGenerationFailed() {
        AccountCreateReq request = createAccountCreateReq("생활비 계좌", AccountType.DEPOSIT);

        when(userRepository.findById(1L)).thenReturn(Optional.of(verifiedUser));
        when(accountRepository.existsByAccountNumber(any(String.class))).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NUMBER_GENERATION_FAILED);
    }

    @Test
    @DisplayName("사용자 ID로 내 계좌 목록을 조회한다")
    void getAccounts() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");

        when(accountRepository.findAllByUserIdAndStatusNot(1L, AccountStatus.CLOSED)).thenReturn(List.of(account));

        List<AccountSummaryRes> responses = accountService.getAccounts(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).accountNumber()).isEqualTo("100200300001");
        assertThat(responses.get(0).nickname()).isEqualTo("생활비 계좌");
        assertThat(responses.get(0).balance()).isZero();
        assertThat(responses.get(0).status()).isEqualTo(AccountStatus.ACTIVE);
    }



    @Test
    @DisplayName("사용자 ID로 해지 계좌 목록을 조회한다")
    void getClosedAccounts() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        ReflectionTestUtils.setField(account, "status", AccountStatus.CLOSED);

        when(accountRepository.findAllByUserIdAndStatus(1L, AccountStatus.CLOSED)).thenReturn(List.of(account));

        List<AccountSummaryRes> responses = accountService.getClosedAccounts(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).accountNumber()).isEqualTo("100200300001");
        assertThat(responses.get(0).nickname()).isEqualTo("생활비 계좌");
        assertThat(responses.get(0).status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    @DisplayName("사용자 ID와 계좌 ID로 내 계좌 별칭을 수정한다")
    void updateNickname() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        AccountNicknameUpdateReq request = new AccountNicknameUpdateReq("급여 계좌");

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));

        AccountDetailRes response = accountService.updateNickname(1L, 1L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("급여 계좌");
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("내 계좌가 아니거나 존재하지 않는 계좌는 별칭 수정에 실패한다")
    void updateNicknameWithNotFoundAccount() {
        AccountNicknameUpdateReq request = new AccountNicknameUpdateReq("급여 계좌");

        when(accountRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateNickname(1L, 999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }



    @Test
    @DisplayName("ACTIVE 상태가 아닌 계좌는 별칭을 수정할 수 없다")
    void updateNicknameWithNotActiveStatus() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        ReflectionTestUtils.setField(account, "status", AccountStatus.CLOSED);
        AccountNicknameUpdateReq request = new AccountNicknameUpdateReq("급여 계좌");

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.updateNickname(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
    }


    @Test
    @DisplayName("계좌 비밀번호를 최초 설정한다")
    void setPassword() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        AccountPasswordSetReq request = new AccountPasswordSetReq("123456");

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");

        AccountPasswordRes response = accountService.setPassword(1L, 1L, request);

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.passwordSet()).isTrue();
        assertThat(account.getAccountPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    @DisplayName("이미 비밀번호가 설정된 계좌는 최초 설정에 실패한다")
    void setPasswordAlreadySet() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        ReflectionTestUtils.setField(account, "accountPasswordHash", "encoded-password");
        AccountPasswordSetReq request = new AccountPasswordSetReq("123456");

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.setPassword(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_PASSWORD_ALREADY_SET);

        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    @DisplayName("현재 비밀번호가 일치하면 계좌 비밀번호를 변경한다")
    void changePassword() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        ReflectionTestUtils.setField(account, "accountPasswordHash", "encoded-password");
        AccountPasswordChangeReq request = new AccountPasswordChangeReq("123456", "654321");

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("654321")).thenReturn("new-encoded-password");

        AccountPasswordRes response = accountService.changePassword(1L, 1L, request);

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.passwordSet()).isTrue();
        assertThat(account.getAccountPasswordHash()).isEqualTo("new-encoded-password");
    }

    @Test
    @DisplayName("현재 비밀번호와 새 비밀번호가 같으면 계좌 비밀번호 변경에 실패한다")
    void changePasswordSameAsCurrent() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        ReflectionTestUtils.setField(account, "accountPasswordHash", "encoded-password");
        AccountPasswordChangeReq request = new AccountPasswordChangeReq("123456", "123456");

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.changePassword(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_PASSWORD_SAME);

        assertThat(account.getAccountPasswordHash()).isEqualTo("encoded-password");
        verify(passwordEncoder, never()).matches(any(String.class), any(String.class));
        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    @DisplayName("현재 비밀번호가 일치하지 않으면 계좌 비밀번호 변경에 실패한다")
    void changePasswordMismatch() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        ReflectionTestUtils.setField(account, "accountPasswordHash", "encoded-password");
        AccountPasswordChangeReq request = new AccountPasswordChangeReq("000000", "654321");

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("000000", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_PASSWORD_MISMATCH);

        assertThat(account.getAccountPasswordHash()).isEqualTo("encoded-password");
        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    @DisplayName("내 계좌가 아니거나 존재하지 않는 계좌는 비밀번호 설정에 실패한다")
    void setPasswordWithNotFoundAccount() {
        AccountPasswordSetReq request = new AccountPasswordSetReq("123456");

        when(accountRepository.findByIdAndUserIdForUpdate(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.setPassword(1L, 999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 계좌는 비밀번호를 설정할 수 없다")
    void setPasswordWithNotActiveStatus() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        ReflectionTestUtils.setField(account, "status", AccountStatus.CLOSED);
        AccountPasswordSetReq request = new AccountPasswordSetReq("123456");

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.setPassword(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    @DisplayName("비밀번호가 설정되지 않은 계좌는 비밀번호 변경에 실패한다")
    void changePasswordWithoutPasswordSet() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        AccountPasswordChangeReq request = new AccountPasswordChangeReq("123456", "654321");

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.changePassword(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_PASSWORD_NOT_SET);

        verify(passwordEncoder, never()).matches(any(String.class), any(String.class));
        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 계좌는 비밀번호를 변경할 수 없다")
    void changePasswordWithNotActiveStatus() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        ReflectionTestUtils.setField(account, "status", AccountStatus.CLOSED);
        ReflectionTestUtils.setField(account, "accountPasswordHash", "encoded-password");
        AccountPasswordChangeReq request = new AccountPasswordChangeReq("123456", "654321");

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.changePassword(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);

        verify(passwordEncoder, never()).matches(any(String.class), any(String.class));
        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    @DisplayName("잔액이 0원인 ACTIVE 계좌를 해지한다")
    void closeAccount() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        AccountDetailRes response = accountService.closeAccount(1L, 1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 계좌는 해지할 수 없다")
    void closeAccountWithNotActiveStatus() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        ReflectionTestUtils.setField(account, "status", AccountStatus.CLOSED);

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.closeAccount(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("잔액이 0원이 아닌 계좌는 해지할 수 없다")
    void closeAccountWithBalanceNotZero() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");
        ReflectionTestUtils.setField(account, "balance", 1000L);

        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.closeAccount(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_BALANCE_NOT_ZERO);
    }

    @Test
    @DisplayName("사용자 ID와 계좌 ID로 내 계좌 상세를 조회한다")
    void getAccount() {
        Account account = createAccount(1L, verifiedUser, "100200300001", "생활비 계좌");

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));

        AccountDetailRes response = accountService.getAccount(1L, 1L);

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
