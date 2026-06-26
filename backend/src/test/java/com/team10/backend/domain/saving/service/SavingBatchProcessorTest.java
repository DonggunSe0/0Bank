package com.team10.backend.domain.saving.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.saving.dto.res.MaturityRes;
import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.entity.SavingProduct;
import com.team10.backend.domain.saving.exception.SavingErrorCode;
import com.team10.backend.domain.saving.repository.DepositRepository;
import com.team10.backend.domain.saving.repository.InstallmentRepository;
import com.team10.backend.domain.saving.type.DepositStatus;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import com.team10.backend.domain.saving.type.SavingProductType;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingBatchProcessorTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 6, 23, 0, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDate TODAY = LocalDate.now(FIXED_CLOCK);

    @Mock
    private TransactionHistoryRepository transactionHistoryRepository;

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private InstallmentRepository installmentRepository;

    @Spy
    private Clock clock = FIXED_CLOCK;

    @InjectMocks
    private SavingBatchProcessor savingBatchProcessor;

    private User user;
    private Account activeAccount;
    private SavingProduct depositProduct;
    private SavingProduct installmentProduct;

    @BeforeEach
    void setUp() {
        user = createUser(1L);
        activeAccount = createAccount(1L, user, AccountStatus.ACTIVE);
        depositProduct = createSavingProduct(1L);
        installmentProduct = createInstallmentProduct(2L);
    }

    @Test
    @DisplayName("예금 1건을 만기 처리한다")
    void matureDeposit() {
        Deposit deposit = createDeposit(1L, DepositStatus.ACTIVE);
        ReflectionTestUtils.setField(deposit, "maturityDate", TODAY);

        when(depositRepository.findByIdWithAccountForUpdate(1L))
                .thenReturn(Optional.of(deposit));

        MaturityRes response = savingBatchProcessor.matureDeposit(1L);

        assertThat(response.savingId()).isEqualTo(1L);
        assertThat(response.savingType()).isEqualTo(SavingProductType.DEPOSIT);
        assertThat(response.principalAmount()).isEqualTo(1000000L);
        assertThat(response.interestAmount()).isEqualTo(35000L);
        assertThat(response.payoutAmount()).isEqualTo(1035000L);
        assertThat(response.status()).isEqualTo("MATURED");
        assertThat(activeAccount.getBalance()).isEqualTo(3035000L);
        assertThat(deposit.getSavingAccount().getBalance()).isZero();
        assertThat(deposit.getSavingAccount().getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.MATURED);

        ArgumentCaptor<TransactionHistory> captor = forClass(TransactionHistory.class);
        verify(transactionHistoryRepository, times(2)).save(captor.capture());
        List<TransactionHistory> histories = captor.getAllValues();
        TransactionHistory savingHistory = histories.get(0);
        assertThat(savingHistory.getType()).isEqualTo(TransactionType.SAVING_MATURITY);
        assertThat(savingHistory.getDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(savingHistory.getAmount()).isEqualTo(1000000L);
        assertThat(savingHistory.getBalanceBefore()).isEqualTo(1000000L);
        assertThat(savingHistory.getBalanceAfter()).isZero();
        assertThat(savingHistory.getMemo()).isEqualTo("예금 만기 출금");

        TransactionHistory history = histories.get(1);
        assertThat(history.getType()).isEqualTo(TransactionType.SAVING_MATURITY);
        assertThat(history.getDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(history.getAmount()).isEqualTo(1035000L);
        assertThat(history.getBalanceBefore()).isEqualTo(2000000L);
        assertThat(history.getBalanceAfter()).isEqualTo(3035000L);
        assertThat(history.getMemo()).isEqualTo("예금 만기 지급");
    }

    @Test
    @DisplayName("적금 1건을 만기 처리한다")
    void matureInstallment() {
        Installment installment = createInstallment(1L, InstallmentStatus.ACTIVE);
        ReflectionTestUtils.setField(installment, "maturityDate", TODAY);

        when(installmentRepository.findByIdWithAccountForUpdate(1L))
                .thenReturn(Optional.of(installment));

        MaturityRes response = savingBatchProcessor.matureInstallment(1L);

        assertThat(response.savingId()).isEqualTo(1L);
        assertThat(response.savingType()).isEqualTo(SavingProductType.INSTALLMENT);
        assertThat(response.principalAmount()).isEqualTo(100000L);
        assertThat(response.interestAmount()).isEqualTo(19500L);
        assertThat(response.payoutAmount()).isEqualTo(119500L);
        assertThat(response.status()).isEqualTo("MATURED");
        assertThat(activeAccount.getBalance()).isEqualTo(2119500L);
        assertThat(installment.getSavingAccount().getBalance()).isZero();
        assertThat(installment.getSavingAccount().getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.MATURED);

        ArgumentCaptor<TransactionHistory> captor = forClass(TransactionHistory.class);
        verify(transactionHistoryRepository, times(2)).save(captor.capture());
        List<TransactionHistory> histories = captor.getAllValues();
        TransactionHistory savingHistory = histories.get(0);
        assertThat(savingHistory.getType()).isEqualTo(TransactionType.SAVING_MATURITY);
        assertThat(savingHistory.getDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(savingHistory.getAmount()).isEqualTo(100000L);
        assertThat(savingHistory.getBalanceBefore()).isEqualTo(100000L);
        assertThat(savingHistory.getBalanceAfter()).isZero();
        assertThat(savingHistory.getMemo()).isEqualTo("적금 만기 출금");

        TransactionHistory history = histories.get(1);
        assertThat(history.getType()).isEqualTo(TransactionType.SAVING_MATURITY);
        assertThat(history.getDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(history.getAmount()).isEqualTo(119500L);
        assertThat(history.getBalanceBefore()).isEqualTo(2000000L);
        assertThat(history.getBalanceAfter()).isEqualTo(2119500L);
        assertThat(history.getMemo()).isEqualTo("적금 만기 지급");
    }

    @Test
    @DisplayName("가입중 상태가 아니면 만기 처리에 실패한다")
    void matureSavingWithNotActiveStatus() {
        Deposit deposit = createDeposit(1L, DepositStatus.MATURED);

        when(depositRepository.findByIdWithAccountForUpdate(1L))
                .thenReturn(Optional.of(deposit));

        assertThatThrownBy(() -> savingBatchProcessor.matureDeposit(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.SAVING_MATURITY_NOT_ALLOWED);
    }

    @Test
    @DisplayName("만기일이 아직 지나지 않으면 만기 처리에 실패한다")
    void matureSavingBeforeMaturityDate() {
        Deposit deposit = createDeposit(1L, DepositStatus.ACTIVE);

        when(depositRepository.findByIdWithAccountForUpdate(1L))
                .thenReturn(Optional.of(deposit));

        assertThatThrownBy(() -> savingBatchProcessor.matureDeposit(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.SAVING_NOT_MATURED_YET);
    }

    @Test
    @DisplayName("적금 월 납입 1건을 처리한다")
    void processInstallmentPayment() {
        Installment installment = createInstallment(1L, InstallmentStatus.ACTIVE);
        ReflectionTestUtils.setField(installment, "nextPaymentDate", TODAY);

        when(installmentRepository.findByIdWithAccountForUpdate(1L))
                .thenReturn(Optional.of(installment));

        savingBatchProcessor.processInstallmentPayment(1L);

        assertThat(activeAccount.getBalance()).isEqualTo(1900000L);
        assertThat(installment.getSavingAccount().getBalance()).isEqualTo(200000L);
        assertThat(installment.getPaidAmount()).isEqualTo(200000L);
        assertThat(installment.getNextPaymentDate()).isEqualTo(TODAY.plusMonths(1));
        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.ACTIVE);

        ArgumentCaptor<TransactionHistory> captor = forClass(TransactionHistory.class);
        verify(transactionHistoryRepository, times(2)).save(captor.capture());
        List<TransactionHistory> histories = captor.getAllValues();
        TransactionHistory withdrawHistory = histories.get(0);
        assertThat(withdrawHistory.getType()).isEqualTo(TransactionType.INSTALLMENT_PAYMENT);
        assertThat(withdrawHistory.getDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(withdrawHistory.getAmount()).isEqualTo(100000L);
        assertThat(withdrawHistory.getBalanceBefore()).isEqualTo(2000000L);
        assertThat(withdrawHistory.getBalanceAfter()).isEqualTo(1900000L);
        assertThat(withdrawHistory.getMemo()).isEqualTo("적금 월 납입 자동이체");

        TransactionHistory savingHistory = histories.get(1);
        assertThat(savingHistory.getType()).isEqualTo(TransactionType.INSTALLMENT_PAYMENT);
        assertThat(savingHistory.getDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(savingHistory.getAmount()).isEqualTo(100000L);
        assertThat(savingHistory.getBalanceBefore()).isEqualTo(100000L);
        assertThat(savingHistory.getBalanceAfter()).isEqualTo(200000L);
        assertThat(savingHistory.getMemo()).isEqualTo("적금 계좌 월 납입 입금");
    }


    @Test
    @DisplayName("잔액이 부족하면 납입 실패 상태로 변경한다")
    void processInstallmentPaymentWithInsufficientBalance() {
        Installment installment = createInstallment(1L, InstallmentStatus.ACTIVE);
        ReflectionTestUtils.setField(installment, "nextPaymentDate", TODAY);
        ReflectionTestUtils.setField(activeAccount, "balance", 50000L);

        when(installmentRepository.findByIdWithAccountForUpdate(1L))
                .thenReturn(Optional.of(installment));

        savingBatchProcessor.processInstallmentPayment(1L);

        assertThat(activeAccount.getBalance()).isEqualTo(50000L);
        assertThat(installment.getPaidAmount()).isEqualTo(100000L);
        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.PAYMENT_FAILED);
        assertThat(installment.getPaymentRetryCount()).isEqualTo(1);
        assertThat(installment.getLastPaymentFailedDate()).isEqualTo(TODAY);
        assertThat(installment.getNextPaymentRetryDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(installment.getPaymentFailureReason()).isEqualTo("잔액 부족");
        verify(transactionHistoryRepository, never()).save(any(TransactionHistory.class));
    }

    @Test
    @DisplayName("출금 계좌가 비활성이면 납입 실패 상태로 변경한다")
    void processInstallmentPaymentWithInactiveAccount() {
        Installment installment = createInstallment(1L, InstallmentStatus.ACTIVE);
        ReflectionTestUtils.setField(installment, "nextPaymentDate", TODAY);
        ReflectionTestUtils.setField(activeAccount, "status", AccountStatus.CLOSED);

        when(installmentRepository.findByIdWithAccountForUpdate(1L))
                .thenReturn(Optional.of(installment));

        savingBatchProcessor.processInstallmentPayment(1L);

        assertThat(activeAccount.getBalance()).isEqualTo(2000000L);
        assertThat(installment.getPaidAmount()).isEqualTo(100000L);
        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.PAYMENT_FAILED);
        assertThat(installment.getPaymentRetryCount()).isEqualTo(1);
        assertThat(installment.getPaymentFailureReason()).isEqualTo("출금 계좌 비활성");
        verify(transactionHistoryRepository, never()).save(any(TransactionHistory.class));
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
        Account account = Account.create(user, "031412345678", "생활비 계좌", AccountType.DEPOSIT);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "status", status);
        ReflectionTestUtils.setField(account, "balance", 2000000L);
        return account;
    }

    private Account createSavingAccount(Long id, AccountType accountType, Long balance) {
        Account account = Account.create(user, "09141234567" + id, "예적금 계좌", accountType);
        ReflectionTestUtils.setField(account, "id", id);
        ReflectionTestUtils.setField(account, "balance", balance);
        return account;
    }

    private Deposit createDeposit(Long id, DepositStatus status) {
        Deposit deposit = Deposit.create(
                user,
                depositProduct,
                activeAccount,
                createSavingAccount(100L + id, AccountType.SAVING_DEPOSIT, 1000000L),
                1000000L,
                3.5,
                TODAY.plusMonths(12),
                35000L
        );
        ReflectionTestUtils.setField(deposit, "id", id);
        ReflectionTestUtils.setField(deposit, "status", status);
        return deposit;
    }

    private Installment createInstallment(Long id, InstallmentStatus status) {
        Installment installment = Installment.create(
                user,
                installmentProduct,
                activeAccount,
                createSavingAccount(200L + id, AccountType.SAVING_INSTALLMENT, 100000L),
                100000L,
                1200000L,
                3.0,
                TODAY.plusMonths(12),
                true
        );
        ReflectionTestUtils.setField(installment, "id", id);
        ReflectionTestUtils.setField(installment, "status", status);
        return installment;
    }

    private SavingProduct createSavingProduct(Long id) {
        SavingProduct product = SavingProduct.builder()
                .name("정기예금")
                .bankName("국민은행")
                .bankCode("KB")
                .type(SavingProductType.DEPOSIT)
                .interestRate(3.5)
                .periodMonth(12)
                .minAmount(100000L)
                .maxAmount(10000000L)
                .monthlyLimit(null)
                .terms("가입 조건")
                .active(true)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private SavingProduct createInstallmentProduct(Long id) {
        SavingProduct product = SavingProduct.builder()
                .name("정기적금")
                .bankName("국민은행")
                .bankCode("KB")
                .type(SavingProductType.INSTALLMENT)
                .interestRate(3.0)
                .periodMonth(12)
                .minAmount(10000L)
                .maxAmount(null)
                .monthlyLimit(500000L)
                .terms("가입 조건")
                .active(true)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
