package com.team10.backend.domain.investment.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountCloseReq;
import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountCreateReq;
import com.team10.backend.domain.investment.account.dto.req.InvestmentAccountUpdateReq;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountCloseRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountCreateRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountDetailRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountSummaryRes;
import com.team10.backend.domain.investment.account.dto.res.InvestmentAccountUpdateRes;
import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import com.team10.backend.domain.investment.account.repository.InvestmentAccountRepository;
import com.team10.backend.domain.investment.account.type.InvestmentAccountStatus;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.portfolio.repository.InvestmentHoldingRepository;
import com.team10.backend.domain.investment.type.CurrencyCode;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentAccountServiceTest {

    private static final Long INITIAL_CASH_BALANCE = 5_000_000L;

    @Mock
    private InvestmentAccountRepository investmentAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private InvestmentHoldingRepository investmentHoldingRepository;

    @InjectMocks
    private InvestmentAccountService investmentAccountService;

    private User verifiedUser;
    private User unverifiedUser;

    @BeforeEach
    void setUp() {
        verifiedUser = createUser(1L, true);
        unverifiedUser = createUser(2L, false);
    }

    @Test
    @DisplayName("사용자 ID로 해지되지 않은 투자 계좌 목록을 조회한다")
    void getAccounts() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");

        when(investmentAccountRepository.findAllByUserIdAndStatusNot(1L, InvestmentAccountStatus.CLOSED))
                .thenReturn(List.of(account));

        List<InvestmentAccountSummaryRes> responses = investmentAccountService.getAccounts(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).accountNumber()).isEqualTo("1234567890-12");
        assertThat(responses.get(0).nickname()).isEqualTo("모의투자 계좌");
        assertThat(responses.get(0).cashBalance()).isEqualTo(INITIAL_CASH_BALANCE);
        assertThat(responses.get(0).currencyCode()).isEqualTo(CurrencyCode.KRW);
        assertThat(responses.get(0).status()).isEqualTo(InvestmentAccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("사용자 ID와 계좌 ID로 해지되지 않은 투자 계좌 상세를 조회한다")
    void getAccount() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");

        when(investmentAccountRepository.findByIdAndUserIdAndStatusNot(1L, 1L, InvestmentAccountStatus.CLOSED))
                .thenReturn(Optional.of(account));

        InvestmentAccountDetailRes response = investmentAccountService.getAccount(1L, 1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.accountNumber()).isEqualTo("1234567890-12");
        assertThat(response.nickname()).isEqualTo("모의투자 계좌");
        assertThat(response.cashBalance()).isEqualTo(INITIAL_CASH_BALANCE);
        assertThat(response.currencyCode()).isEqualTo(CurrencyCode.KRW);
        assertThat(response.status()).isEqualTo(InvestmentAccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("내 계좌가 아니거나 해지된 투자 계좌는 상세 조회에 실패한다")
    void getAccountWithNotFoundAccount() {
        when(investmentAccountRepository.findByIdAndUserIdAndStatusNot(999L, 1L, InvestmentAccountStatus.CLOSED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> investmentAccountService.getAccount(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("본인인증 완료 사용자는 투자 계좌를 개설할 수 있다")
    void createAccount() {
        InvestmentAccountCreateReq request =
                new InvestmentAccountCreateReq("모의투자 계좌", "123456", CurrencyCode.KRW);

        when(userRepository.findById(1L)).thenReturn(Optional.of(verifiedUser));
        when(investmentAccountRepository.existsByAccountNumber(any(String.class))).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(investmentAccountRepository.save(any(InvestmentAccount.class))).thenAnswer(invocation -> {
            InvestmentAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 1L);
            return account;
        });

        InvestmentAccountCreateRes response = investmentAccountService.createAccount(1L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.accountNumber()).matches("\\d{10}-\\d{2}");
        assertThat(response.nickname()).isEqualTo("모의투자 계좌");
        assertThat(response.cashBalance()).isEqualTo(INITIAL_CASH_BALANCE);
        assertThat(response.currencyCode()).isEqualTo(CurrencyCode.KRW);
        assertThat(response.status()).isEqualTo(InvestmentAccountStatus.ACTIVE);

        ArgumentCaptor<InvestmentAccount> captor = ArgumentCaptor.forClass(InvestmentAccount.class);
        verify(investmentAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 투자 계좌를 개설할 수 없다")
    void createAccountWithNotFoundUser() {
        InvestmentAccountCreateReq request =
                new InvestmentAccountCreateReq("모의투자 계좌", "123456", CurrencyCode.KRW);

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> investmentAccountService.createAccount(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("본인인증 미완료 사용자는 투자 계좌를 개설할 수 없다")
    void createAccountWithoutIdentityVerification() {
        InvestmentAccountCreateReq request =
                new InvestmentAccountCreateReq("모의투자 계좌", "123456", CurrencyCode.KRW);

        when(userRepository.findById(2L)).thenReturn(Optional.of(unverifiedUser));

        assertThatThrownBy(() -> investmentAccountService.createAccount(2L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.IDENTITY_VERIFICATION_REQUIRED);
    }

    @Test
    @DisplayName("계좌번호 생성 최대 시도 횟수를 초과하면 투자 계좌 개설에 실패한다")
    void createAccountWithAccountNumberGenerationFailed() {
        InvestmentAccountCreateReq request =
                new InvestmentAccountCreateReq("모의투자 계좌", "123456", CurrencyCode.KRW);

        when(userRepository.findById(1L)).thenReturn(Optional.of(verifiedUser));
        when(investmentAccountRepository.existsByAccountNumber(any(String.class))).thenReturn(true);

        assertThatThrownBy(() -> investmentAccountService.createAccount(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_NUMBER_GENERATION_FAILED);
    }

    @Test
    @DisplayName("투자 계좌 비밀번호가 일치하면 전달된 별칭만 수정한다")
    void updateAccountWithNicknameOnly() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", "장기투자 계좌", null);

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);

        InvestmentAccountUpdateRes response = investmentAccountService.updateAccount(1L, 1L, request);

        assertThat(response.nickname()).isEqualTo("장기투자 계좌");
        assertThat(response.updatedAt()).isEqualTo(account.getUpdatedAt());
        assertThat(account.getNickname()).isEqualTo("장기투자 계좌");
        assertThat(account.getAccountPasswordHash()).isEqualTo("encoded-password");
        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    @DisplayName("투자 계좌 비밀번호가 일치하지 않으면 정보 수정에 실패한다")
    void updateAccountWithPasswordMismatch() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("000000", "장기투자 계좌", null);

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("000000", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> investmentAccountService.updateAccount(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_PASSWORD_MISMATCH);

        assertThat(account.getNickname()).isEqualTo("모의투자 계좌");
        assertThat(account.getAccountPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 투자 계좌는 정보를 수정할 수 없다")
    void updateAccountWithNotActiveStatus() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        ReflectionTestUtils.setField(account, "status", InvestmentAccountStatus.CLOSED);
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", "장기투자 계좌", null);

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> investmentAccountService.updateAccount(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_ACTIVE);

        verify(passwordEncoder, never()).matches(any(String.class), any(String.class));
    }

    @Test
    @DisplayName("투자 계좌 비밀번호가 일치하면 전달된 새 비밀번호만 수정한다")
    void updateAccountWithPasswordOnly() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", null, "654321");

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("654321")).thenReturn("new-encoded-password");

        InvestmentAccountUpdateRes response = investmentAccountService.updateAccount(1L, 1L, request);

        assertThat(response.nickname()).isEqualTo("모의투자 계좌");
        assertThat(response.updatedAt()).isEqualTo(account.getUpdatedAt());
        assertThat(account.getNickname()).isEqualTo("모의투자 계좌");
        assertThat(account.getAccountPasswordHash()).isEqualTo("new-encoded-password");
    }

    @Test
    @DisplayName("투자 계좌 비밀번호가 일치하면 별칭과 새 비밀번호를 함께 수정한다")
    void updateAccountWithNicknameAndPassword() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", "장기투자 계좌", "654321");

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("654321")).thenReturn("new-encoded-password");

        InvestmentAccountUpdateRes response = investmentAccountService.updateAccount(1L, 1L, request);

        assertThat(response.nickname()).isEqualTo("장기투자 계좌");
        assertThat(response.updatedAt()).isEqualTo(account.getUpdatedAt());
        assertThat(account.getNickname()).isEqualTo("장기투자 계좌");
        assertThat(account.getAccountPasswordHash()).isEqualTo("new-encoded-password");
    }

    @Test
    @DisplayName("수정할 값이 없으면 투자 계좌 정보 수정에 실패한다")
    void updateAccountWithoutUpdateValue() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", null, null);

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> investmentAccountService.updateAccount(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_UPDATE_VALUE_REQUIRED);

        assertThat(account.getAccountPasswordHash()).isEqualTo("encoded-password");
        verify(passwordEncoder, never()).encode(any(String.class));
    }

    @Test
    @DisplayName("내 계좌가 아니거나 존재하지 않는 투자 계좌는 수정할 수 없다")
    void updateAccountWithNotFoundAccount() {
        InvestmentAccountUpdateReq request =
                new InvestmentAccountUpdateReq("123456", null, "654321");

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> investmentAccountService.updateAccount(1L, 999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("예수금과 보유 종목이 없고 비밀번호가 일치하면 투자 계좌를 해지한다")
    void closeAccount() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        InvestmentAccountCloseReq request = new InvestmentAccountCloseReq("123456");
        ReflectionTestUtils.setField(account, "cashBalance", 0L);

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(investmentHoldingRepository.existsByInvestmentAccountId(1L)).thenReturn(false);

        InvestmentAccountCloseRes response = investmentAccountService.closeAccount(1L, 1L, request);

        assertThat(response.status()).isEqualTo(InvestmentAccountStatus.CLOSED);
        assertThat(account.getStatus()).isEqualTo(InvestmentAccountStatus.CLOSED);
    }

    @Test
    @DisplayName("투자 계좌 비밀번호가 일치하지 않으면 해지에 실패한다")
    void closeAccountWithPasswordMismatch() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        InvestmentAccountCloseReq request = new InvestmentAccountCloseReq("000000");

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("000000", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> investmentAccountService.closeAccount(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_PASSWORD_MISMATCH);

        assertThat(account.getStatus()).isEqualTo(InvestmentAccountStatus.ACTIVE);
        verify(investmentHoldingRepository, never()).existsByInvestmentAccountId(any(Long.class));
    }

    @Test
    @DisplayName("예수금이 남아 있으면 투자 계좌를 해지할 수 없다")
    void closeAccountWithCashBalance() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        InvestmentAccountCloseReq request = new InvestmentAccountCloseReq("123456");

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> investmentAccountService.closeAccount(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_CASH_BALANCE_NOT_ZERO);

        assertThat(account.getStatus()).isEqualTo(InvestmentAccountStatus.ACTIVE);
        verify(investmentHoldingRepository, never()).existsByInvestmentAccountId(any(Long.class));
    }

    @Test
    @DisplayName("보유 종목이 있으면 투자 계좌를 해지할 수 없다")
    void closeAccountWithHolding() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        InvestmentAccountCloseReq request = new InvestmentAccountCloseReq("123456");
        ReflectionTestUtils.setField(account, "cashBalance", 0L);

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(investmentHoldingRepository.existsByInvestmentAccountId(1L)).thenReturn(true);

        assertThatThrownBy(() -> investmentAccountService.closeAccount(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_HOLDING_EXISTS);

        assertThat(account.getStatus()).isEqualTo(InvestmentAccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 투자 계좌는 해지할 수 없다")
    void closeAccountWithNotActiveStatus() {
        InvestmentAccount account = createInvestmentAccount(1L, verifiedUser, "모의투자 계좌");
        ReflectionTestUtils.setField(account, "status", InvestmentAccountStatus.CLOSED);
        InvestmentAccountCloseReq request = new InvestmentAccountCloseReq("123456");

        when(investmentAccountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> investmentAccountService.closeAccount(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_ACTIVE);

        verify(passwordEncoder, never()).matches(any(String.class), any(String.class));
    }

    private User createUser(Long id, boolean identityVerified) {
        User user = User.create(
                "user" + id + "@example.com",
                "encoded-password",
                "사용자" + id,
                "01012345678",
                LocalDate.of(1995, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "identityVerified", identityVerified);
        return user;
    }

    private InvestmentAccount createInvestmentAccount(Long id, User user, String nickname) {
        InvestmentAccount account = InvestmentAccount.create(
                user,
                "1234567890-12",
                nickname,
                "encoded-password",
                INITIAL_CASH_BALANCE,
                CurrencyCode.KRW
        );
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
