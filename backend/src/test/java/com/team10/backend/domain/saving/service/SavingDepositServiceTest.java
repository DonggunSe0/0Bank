package com.team10.backend.domain.saving.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.saving.dto.req.DepositCreateReq;
import com.team10.backend.domain.saving.dto.req.InstallmentCreateReq;
import com.team10.backend.domain.saving.dto.res.DepositCreateRes;
import com.team10.backend.domain.saving.dto.res.DepositDetailRes;
import com.team10.backend.domain.saving.dto.res.DepositSummaryRes;
import com.team10.backend.domain.saving.dto.res.InstallmentCreateRes;
import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.entity.SavingProduct;
import com.team10.backend.domain.saving.exception.SavingErrorCode;
import com.team10.backend.domain.saving.repository.DepositRepository;
import com.team10.backend.domain.saving.repository.InstallmentRepository;
import com.team10.backend.domain.saving.repository.SavingProductRepository;
import com.team10.backend.domain.saving.type.DepositStatus;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import com.team10.backend.domain.saving.type.SavingProductType;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
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
class SavingDepositServiceTest {

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private SavingProductRepository savingProductRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InstallmentRepository installmentRepository;

    @InjectMocks
    private SavingDepositService savingDepositService;

    private User user;
    private Account activeAccount;
    private SavingProduct depositProduct;
    private SavingProduct installmentProduct;

    @BeforeEach
    void setUp() {
        user = createUser(1L);
        activeAccount = createAccount(1L, user, AccountStatus.ACTIVE);
        depositProduct = createSavingProduct(1L, 100000L, 10000000L);
        installmentProduct = createInstallmentProduct(2L, 10000L, 500000L);
    }

    @Test
    @DisplayName("예금 가입 요청이 유효하면 예금 가입 정보를 저장한다")
    void createDeposit() {
        DepositCreateReq request = new DepositCreateReq(1L, 1L, 1000000L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(1L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.of(depositProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeAccount));
        when(depositRepository.save(any(Deposit.class))).thenAnswer(invocation -> {
            Deposit deposit = invocation.getArgument(0);
            ReflectionTestUtils.setField(deposit, "id", 1L);
            return deposit;
        });

        DepositCreateRes response = savingDepositService.createDeposit(1L, request);

        assertThat(response.depositId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(DepositStatus.ACTIVE);
        assertThat(response.principal()).isEqualTo(1000000L);
        assertThat(response.maturityDate()).isEqualTo(LocalDate.now().plusMonths(12));
        assertThat(response.expectedInterest()).isEqualTo(35000L);
        assertThat(activeAccount.getBalance()).isEqualTo(1000000L);
        verify(depositRepository).save(any(Deposit.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 예금에 가입할 수 없다")
    void createDepositWithNotFoundUser() {
        DepositCreateReq request = new DepositCreateReq(1L, 1L, 1000000L);

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savingDepositService.createDeposit(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않거나 비활성 예금 상품이면 예금 가입에 실패한다")
    void createDepositWithNotFoundProduct() {
        DepositCreateReq request = new DepositCreateReq(999L, 1L, 1000000L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(999L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> savingDepositService.createDeposit(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("출금 계좌가 본인 소유가 아니거나 존재하지 않으면 예금 가입에 실패한다")
    void createDepositWithNotFoundWithdrawAccount() {
        DepositCreateReq request = new DepositCreateReq(1L, 999L, 1000000L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(1L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.of(depositProduct));
        when(accountRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savingDepositService.createDeposit(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 계좌로는 예금에 가입할 수 없다")
    void createDepositWithNotActiveWithdrawAccount() {
        DepositCreateReq request = new DepositCreateReq(1L, 1L, 1000000L);
        Account closedAccount = createAccount(1L, user, AccountStatus.CLOSED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(1L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.of(depositProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(closedAccount));

        assertThatThrownBy(() -> savingDepositService.createDeposit(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("최소 가입 금액보다 작으면 예금 가입에 실패한다")
    void createDepositWithLessThanMinAmount() {
        DepositCreateReq request = new DepositCreateReq(1L, 1L, 50000L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(1L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.of(depositProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> savingDepositService.createDeposit(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.INVALID_DEPOSIT_AMOUNT);
    }

    @Test
    @DisplayName("최대 가입 금액보다 크면 예금 가입에 실패한다")
    void createDepositWithGreaterThanMaxAmount() {
        DepositCreateReq request = new DepositCreateReq(1L, 1L, 20000000L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(1L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.of(depositProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> savingDepositService.createDeposit(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.INVALID_DEPOSIT_AMOUNT);
    }

    @Test
    @DisplayName("출금 계좌 잔액이 부족하면 예금 가입에 실패한다")
    void createDepositWithInsufficientBalance() {
        DepositCreateReq request = new DepositCreateReq(1L, 1L, 1000000L);
        Account insufficientAccount = createAccount(1L, user, AccountStatus.ACTIVE, 500000L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(1L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.of(depositProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(insufficientAccount));

        assertThatThrownBy(() -> savingDepositService.createDeposit(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("상태 필터 없이 내 예금 목록을 조회한다")
    void getDeposits() {
        Deposit deposit = createDeposit(1L, DepositStatus.ACTIVE);

        when(depositRepository.findAllByUserIdWithProduct(1L)).thenReturn(List.of(deposit));

        List<DepositSummaryRes> responses = savingDepositService.getDeposits(1L, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).depositId()).isEqualTo(1L);
        assertThat(responses.get(0).productName()).isEqualTo("정기예금");
        assertThat(responses.get(0).bankName()).isEqualTo("국민은행");
        assertThat(responses.get(0).principal()).isEqualTo(1000000L);
        assertThat(responses.get(0).status()).isEqualTo(DepositStatus.ACTIVE);
        verify(depositRepository).findAllByUserIdWithProduct(1L);
    }

    @Test
    @DisplayName("상태 필터로 내 예금 목록을 조회한다")
    void getDepositsWithStatus() {
        Deposit deposit = createDeposit(1L, DepositStatus.MATURED);

        when(depositRepository.findAllByUserIdAndStatusWithProduct(1L, DepositStatus.MATURED))
                .thenReturn(List.of(deposit));

        List<DepositSummaryRes> responses = savingDepositService.getDeposits(1L, DepositStatus.MATURED);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).depositId()).isEqualTo(1L);
        assertThat(responses.get(0).status()).isEqualTo(DepositStatus.MATURED);
        verify(depositRepository).findAllByUserIdAndStatusWithProduct(1L, DepositStatus.MATURED);
    }


    @Test
    @DisplayName("내 예금 상세를 조회한다")
    void getDeposit() {
        Deposit deposit = createDeposit(1L, DepositStatus.ACTIVE);

        when(depositRepository.findByIdAndUserIdWithProduct(1L, 1L))
                .thenReturn(Optional.of(deposit));

        DepositDetailRes response = savingDepositService.getDeposit(1L, 1L);

        assertThat(response.depositId()).isEqualTo(1L);
        assertThat(response.productName()).isEqualTo("정기예금");
        assertThat(response.bankName()).isEqualTo("국민은행");
        assertThat(response.principal()).isEqualTo(1000000L);
        assertThat(response.interestRate()).isEqualTo(3.5);
        assertThat(response.expectedInterest()).isEqualTo(35000L);
        assertThat(response.maturityDate()).isEqualTo(LocalDate.now().plusMonths(12));
        assertThat(response.status()).isEqualTo(DepositStatus.ACTIVE);
        verify(depositRepository).findByIdAndUserIdWithProduct(1L, 1L);
    }

    @Test
    @DisplayName("본인 예금이 아니거나 존재하지 않으면 상세 조회에 실패한다")
    void getDepositWithNotFoundDeposit() {
        when(depositRepository.findByIdAndUserIdWithProduct(999L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> savingDepositService.getDeposit(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.DEPOSIT_NOT_FOUND);
    }

    @Test
    @DisplayName("적금 가입 요청이 유효하면 적금 가입 정보를 저장한다")
    void createInstallment() {
        InstallmentCreateReq request = new InstallmentCreateReq(
                2L,
                1L,
                100000L,
                1200000L,
                true
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(2L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.of(installmentProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeAccount));
        when(installmentRepository.save(any(Installment.class))).thenAnswer(invocation -> {
            Installment installment = invocation.getArgument(0);
            ReflectionTestUtils.setField(installment, "id", 1L);
            return installment;
        });

        InstallmentCreateRes response = savingDepositService.createInstallment(1L, request);

        assertThat(response.installmentId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(InstallmentStatus.ACTIVE);
        assertThat(response.maturityDate()).isEqualTo(LocalDate.now().plusMonths(12));
        assertThat(response.progressRate()).isEqualTo(8L);
        assertThat(activeAccount.getBalance()).isEqualTo(1900000L);
        verify(installmentRepository).save(any(Installment.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 적금에 가입할 수 없다")
    void createInstallmentWithNotFoundUser() {
        InstallmentCreateReq request = new InstallmentCreateReq(2L, 1L, 100000L, 1200000L, true);

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savingDepositService.createInstallment(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않거나 비활성 적금 상품이면 적금 가입에 실패한다")
    void createInstallmentWithNotFoundProduct() {
        InstallmentCreateReq request = new InstallmentCreateReq(999L, 1L, 100000L, 1200000L, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(999L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> savingDepositService.createInstallment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("출금 계좌가 본인 소유가 아니거나 존재하지 않으면 적금 가입에 실패한다")
    void createInstallmentWithNotFoundWithdrawAccount() {
        InstallmentCreateReq request = new InstallmentCreateReq(2L, 999L, 100000L, 1200000L, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(2L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.of(installmentProduct));
        when(accountRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savingDepositService.createInstallment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 계좌로는 적금에 가입할 수 없다")
    void createInstallmentWithNotActiveWithdrawAccount() {
        InstallmentCreateReq request = new InstallmentCreateReq(2L, 1L, 100000L, 1200000L, true);
        Account closedAccount = createAccount(1L, user, AccountStatus.CLOSED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(2L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.of(installmentProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(closedAccount));

        assertThatThrownBy(() -> savingDepositService.createInstallment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("최소 월 납입액보다 작으면 적금 가입에 실패한다")
    void createInstallmentWithLessThanMinAmount() {
        InstallmentCreateReq request = new InstallmentCreateReq(2L, 1L, 5000L, 60000L, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(2L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.of(installmentProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> savingDepositService.createInstallment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.INVALID_INSTALLMENT_AMOUNT);
    }

    @Test
    @DisplayName("월 납입 한도보다 크면 적금 가입에 실패한다")
    void createInstallmentWithGreaterThanMonthlyLimit() {
        InstallmentCreateReq request = new InstallmentCreateReq(2L, 1L, 600000L, 7200000L, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(2L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.of(installmentProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> savingDepositService.createInstallment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.INVALID_INSTALLMENT_AMOUNT);
    }

    @Test
    @DisplayName("목표 금액이 월 납입액과 가입 기간으로 계산한 값과 다르면 적금 가입에 실패한다")
    void createInstallmentWithInvalidTargetAmount() {
        InstallmentCreateReq request = new InstallmentCreateReq(2L, 1L, 100000L, 1000000L, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(2L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.of(installmentProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeAccount));

        assertThatThrownBy(() -> savingDepositService.createInstallment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.INVALID_TARGET_AMOUNT);
    }

    @Test
    @DisplayName("출금 계좌 잔액이 부족하면 적금 가입에 실패한다")
    void createInstallmentWithInsufficientBalance() {
        InstallmentCreateReq request = new InstallmentCreateReq(2L, 1L, 100000L, 1200000L, true);
        Account insufficientAccount = createAccount(1L, user, AccountStatus.ACTIVE, 50000L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(2L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.of(installmentProduct));
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(insufficientAccount));

        assertThatThrownBy(() -> savingDepositService.createInstallment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.INSUFFICIENT_BALANCE);
    }

    private User createUser(Long id) {
        User user = User.create(
                "user" + id + "@test.com",
                "password",
                "홍길동",
                "01012345678",
                LocalDate.of(1990, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "identityVerified", true);
        return user;
    }

    private Account createAccount(Long id, User user, AccountStatus status) {
        return createAccount(id, user, status, 2000000L);
    }

    private Account createAccount(Long id, User user, AccountStatus status, Long balance) {
        Account account = Account.create(user, "031412345678", "생활비 계좌", AccountType.DEPOSIT);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "status", status);
        ReflectionTestUtils.setField(account, "balance", balance);
        return account;
    }

    private Deposit createDeposit(Long id, DepositStatus status) {
        Deposit deposit = Deposit.create(
                user,
                depositProduct,
                activeAccount,
                1000000L,
                3.5,
                LocalDate.now().plusMonths(12),
                35000L
        );
        ReflectionTestUtils.setField(deposit, "id", id);
        ReflectionTestUtils.setField(deposit, "status", status);
        return deposit;
    }

    private SavingProduct createSavingProduct(Long id, Long minAmount, Long maxAmount) {
        SavingProduct product = SavingProduct.builder()
                .name("정기예금")
                .bankName("국민은행")
                .bankCode("KB")
                .type(SavingProductType.DEPOSIT)
                .interestRate(3.5)
                .periodMonth(12)
                .minAmount(minAmount)
                .maxAmount(maxAmount)
                .monthlyLimit(null)
                .terms("가입 조건")
                .active(true)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private SavingProduct createInstallmentProduct(Long id, Long minAmount, Long monthlyLimit) {
        SavingProduct product = SavingProduct.builder()
                .name("정기적금")
                .bankName("국민은행")
                .bankCode("KB")
                .type(SavingProductType.INSTALLMENT)
                .interestRate(3.0)
                .periodMonth(12)
                .minAmount(minAmount)
                .maxAmount(null)
                .monthlyLimit(monthlyLimit)
                .terms("가입 조건")
                .active(true)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
