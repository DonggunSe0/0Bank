package com.team10.backend.domain.saving.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.saving.dto.req.*;
import com.team10.backend.domain.saving.dto.res.*;
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
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavingDepositServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 6, 23, 0, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDate TODAY = LocalDate.now(FIXED_CLOCK);

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

    @Mock
    private TransactionHistoryRepository transactionHistoryRepository;

    @Mock
    private SavingBatchProcessor savingBatchProcessor;

    @Spy
    private Clock clock = FIXED_CLOCK;

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
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 10L);
            return account;
        });
        when(depositRepository.save(any(Deposit.class))).thenAnswer(invocation -> {
            Deposit deposit = invocation.getArgument(0);
            ReflectionTestUtils.setField(deposit, "id", 1L);
            return deposit;
        });

        DepositCreateRes response = savingDepositService.createDeposit(1L, request);

        assertThat(response.depositId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(DepositStatus.ACTIVE);
        assertThat(response.principal()).isEqualTo(1000000L);
        assertThat(response.maturityDate()).isEqualTo(TODAY.plusMonths(12));
        assertThat(response.expectedInterest()).isEqualTo(35000L);
        assertThat(activeAccount.getBalance()).isEqualTo(1000000L);

        ArgumentCaptor<Account> accountCaptor = forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savingAccount = accountCaptor.getValue();
        assertThat(savingAccount.getAccountType()).isEqualTo(AccountType.SAVING_DEPOSIT);
        assertThat(savingAccount.getBalance()).isEqualTo(1000000L);

        ArgumentCaptor<TransactionHistory> historyCaptor = forClass(TransactionHistory.class);
        verify(transactionHistoryRepository, times(2)).save(historyCaptor.capture());
        List<TransactionHistory> histories = historyCaptor.getAllValues();
        assertThat(histories.get(0).getType()).isEqualTo(TransactionType.SAVING_DEPOSIT_SIGNUP);
        assertThat(histories.get(0).getDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(histories.get(0).getAmount()).isEqualTo(1000000L);
        assertThat(histories.get(0).getBalanceBefore()).isEqualTo(2000000L);
        assertThat(histories.get(0).getBalanceAfter()).isEqualTo(1000000L);
        assertThat(histories.get(1).getType()).isEqualTo(TransactionType.SAVING_DEPOSIT_SIGNUP);
        assertThat(histories.get(1).getDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(histories.get(1).getAmount()).isEqualTo(1000000L);
        assertThat(histories.get(1).getBalanceBefore()).isZero();
        assertThat(histories.get(1).getBalanceAfter()).isEqualTo(1000000L);

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
        when(accountRepository.findByIdAndUserIdForUpdate(999L, 1L)).thenReturn(Optional.empty());

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
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(closedAccount));

        assertThatThrownBy(() -> savingDepositService.createDeposit(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("입출금계좌가 아닌 계좌로는 예금에 가입할 수 없다")
    void createDepositWithSavingWithdrawAccount() {
        DepositCreateReq request = new DepositCreateReq(1L, 1L, 1000000L);
        Account savingAccount = createSavingAccount(1L, AccountType.SAVING_DEPOSIT, 2000000L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(1L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.of(depositProduct));
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(savingAccount));

        assertThatThrownBy(() -> savingDepositService.createDeposit(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_TRANSFER_OUT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("최소 가입 금액보다 작으면 예금 가입에 실패한다")
    void createDepositWithLessThanMinAmount() {
        DepositCreateReq request = new DepositCreateReq(1L, 1L, 50000L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(1L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.of(depositProduct));
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(activeAccount));

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
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(activeAccount));

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
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(insufficientAccount));

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
        assertThat(response.maturityDate()).isEqualTo(TODAY.plusMonths(12));
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
    @DisplayName("상태 필터 없이 내 적금 목록을 조회한다")
    void getInstallments() {
        Installment installment = createInstallment(1L, InstallmentStatus.ACTIVE);

        when(installmentRepository.findAllByUserIdWithProduct(1L))
                .thenReturn(List.of(installment));

        List<InstallmentSummaryRes> responses = savingDepositService.getInstallments(1L, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).installmentId()).isEqualTo(1L);
        assertThat(responses.get(0).productName()).isEqualTo("정기적금");
        assertThat(responses.get(0).bankName()).isEqualTo("국민은행");
        assertThat(responses.get(0).paidAmount()).isEqualTo(100000L);
        assertThat(responses.get(0).progressRate()).isEqualTo(8L);
        assertThat(responses.get(0).status()).isEqualTo(InstallmentStatus.ACTIVE);
        verify(installmentRepository).findAllByUserIdWithProduct(1L);
    }

    @Test
    @DisplayName("상태 필터로 내 적금 목록을 조회한다")
    void getInstallmentsWithStatus() {
        Installment installment = createInstallment(1L, InstallmentStatus.MATURED);

        when(installmentRepository.findAllByUserIdAndStatusWithProduct(1L, InstallmentStatus.MATURED))
                .thenReturn(List.of(installment));

        List<InstallmentSummaryRes> responses =
                savingDepositService.getInstallments(1L, InstallmentStatus.MATURED);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).installmentId()).isEqualTo(1L);
        assertThat(responses.get(0).status()).isEqualTo(InstallmentStatus.MATURED);
        verify(installmentRepository)
                .findAllByUserIdAndStatusWithProduct(1L, InstallmentStatus.MATURED);
    }

    @Test
    @DisplayName("내 적금 상세를 조회한다")
    void getInstallment() {
        Installment installment = createInstallment(1L, InstallmentStatus.ACTIVE);

        when(installmentRepository.findByIdAndUserIdWithProduct(1L, 1L))
                .thenReturn(Optional.of(installment));

        InstallmentDetailRes response = savingDepositService.getInstallment(1L, 1L);

        assertThat(response.installmentId()).isEqualTo(1L);
        assertThat(response.productName()).isEqualTo("정기적금");
        assertThat(response.bankName()).isEqualTo("국민은행");
        assertThat(response.monthlyAmount()).isEqualTo(100000L);
        assertThat(response.paidAmount()).isEqualTo(100000L);
        assertThat(response.targetAmount()).isEqualTo(1200000L);
        assertThat(response.progressRate()).isEqualTo(8L);
        assertThat(response.maturityDate()).isEqualTo(TODAY.plusMonths(12));
        assertThat(response.status()).isEqualTo(InstallmentStatus.ACTIVE);
        verify(installmentRepository).findByIdAndUserIdWithProduct(1L, 1L);
    }

    @Test
    @DisplayName("본인 적금이 아니거나 존재하지 않으면 상세 조회에 실패한다")
    void getInstallmentWithNotFoundInstallment() {
        when(installmentRepository.findByIdAndUserIdWithProduct(999L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> savingDepositService.getInstallment(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.INSTALLMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("내 예금 예상 이자를 조회한다")
    void getDepositInterestPreview() {
        Deposit deposit = createDeposit(1L, DepositStatus.ACTIVE);

        when(depositRepository.findByIdAndUserIdWithProduct(1L, 1L))
                .thenReturn(Optional.of(deposit));

        InterestPreviewRes response =
                savingDepositService.getInterestPreview(1L, 1L, SavingProductType.DEPOSIT);

        assertThat(response.savingId()).isEqualTo(1L);
        assertThat(response.savingType()).isEqualTo(SavingProductType.DEPOSIT);
        assertThat(response.principal()).isEqualTo(1000000L);
        assertThat(response.interestRate()).isEqualTo(3.5);
        assertThat(response.expectedInterest()).isEqualTo(35000L);
        assertThat(response.expectedTotalAmount()).isEqualTo(1035000L);
        verify(depositRepository).findByIdAndUserIdWithProduct(1L, 1L);
    }

    @Test
    @DisplayName("내 적금 예상 이자를 조회한다")
    void getInstallmentInterestPreview() {
        Installment installment = createInstallment(1L, InstallmentStatus.ACTIVE);

        when(installmentRepository.findByIdAndUserIdWithProduct(1L, 1L))
                .thenReturn(Optional.of(installment));

        InterestPreviewRes response =
                savingDepositService.getInterestPreview(1L, 1L, SavingProductType.INSTALLMENT);

        assertThat(response.savingId()).isEqualTo(1L);
        assertThat(response.savingType()).isEqualTo(SavingProductType.INSTALLMENT);
        assertThat(response.principal()).isEqualTo(1200000L);
        assertThat(response.interestRate()).isEqualTo(3.0);
        assertThat(response.expectedInterest()).isEqualTo(19500L);
        assertThat(response.expectedTotalAmount()).isEqualTo(1219500L);
        verify(installmentRepository).findByIdAndUserIdWithProduct(1L, 1L);
    }

    @Test
    @DisplayName("예금 출금 제한을 설정한다")
    void updateDepositWithdrawalLock() {
        Deposit deposit = createDeposit(1L, DepositStatus.ACTIVE);
        WithdrawalLockReq request = new WithdrawalLockReq(
                SavingProductType.DEPOSIT,
                true,
                "목표 저축을 위해 제한"
        );

        when(depositRepository.findByIdAndUserIdWithProduct(1L, 1L))
                .thenReturn(Optional.of(deposit));

        WithdrawalLockRes response =
                savingDepositService.updateWithdrawalLock(1L, 1L, request);

        assertThat(response.savingId()).isEqualTo(1L);
        assertThat(response.savingType()).isEqualTo(SavingProductType.DEPOSIT);
        assertThat(response.lockYn()).isTrue();
        assertThat(response.reason()).isEqualTo("목표 저축을 위해 제한");
        assertThat(deposit.isWithdrawalLocked()).isTrue();
        assertThat(deposit.getWithdrawalLockReason()).isEqualTo("목표 저축을 위해 제한");
        verify(depositRepository).findByIdAndUserIdWithProduct(1L, 1L);
    }

    @Test
    @DisplayName("적금 출금 제한을 설정한다")
    void updateInstallmentWithdrawalLock() {
        Installment installment = createInstallment(1L, InstallmentStatus.ACTIVE);
        WithdrawalLockReq request = new WithdrawalLockReq(
                SavingProductType.INSTALLMENT,
                true,
                "목표 저축을 위해 제한"
        );

        when(installmentRepository.findByIdAndUserIdWithProduct(1L, 1L))
                .thenReturn(Optional.of(installment));

        WithdrawalLockRes response =
                savingDepositService.updateWithdrawalLock(1L, 1L, request);

        assertThat(response.savingId()).isEqualTo(1L);
        assertThat(response.savingType()).isEqualTo(SavingProductType.INSTALLMENT);
        assertThat(response.lockYn()).isTrue();
        assertThat(response.reason()).isEqualTo("목표 저축을 위해 제한");
        assertThat(installment.isWithdrawalLocked()).isTrue();
        assertThat(installment.getWithdrawalLockReason()).isEqualTo("목표 저축을 위해 제한");
        verify(installmentRepository).findByIdAndUserIdWithProduct(1L, 1L);
    }

    @Test
    @DisplayName("출금 제한 해제 사유가 없으면 실패한다")
    void updateWithdrawalLockWithoutUnlockReason() {
        WithdrawalLockReq request = new WithdrawalLockReq(
                SavingProductType.DEPOSIT,
                false,
                " "
        );

        assertThatThrownBy(() -> savingDepositService.updateWithdrawalLock(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.WITHDRAWAL_UNLOCK_REASON_REQUIRED);
    }

    @Test
    @DisplayName("가입중 예금을 중도 해지한다")
    void cancelDeposit() {
        Deposit deposit = createDeposit(1L, DepositStatus.ACTIVE);
        ReflectionTestUtils.setField(deposit, "createdAt", LocalDateTime.of(2025, 12, 23, 0, 0));
        EarlyCancelReq request = new EarlyCancelReq(SavingProductType.DEPOSIT);

        when(depositRepository.findByIdAndUserIdWithAccountForUpdate(1L, 1L))
                .thenReturn(Optional.of(deposit));

        EarlyCancelRes response = savingDepositService.cancelSaving(1L, 1L, request);

        assertThat(response.savingId()).isEqualTo(1L);
        assertThat(response.savingType()).isEqualTo(SavingProductType.DEPOSIT);
        assertThat(response.principalAmount()).isEqualTo(1000000L);
        assertThat(response.interestAmount()).isEqualTo(8750L);
        assertThat(response.refundAmount()).isEqualTo(1008750L);
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(activeAccount.getBalance()).isEqualTo(3008750L);
        assertThat(deposit.getSavingAccount().getBalance()).isZero();
        assertThat(deposit.getSavingAccount().getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.CANCELLED);

        ArgumentCaptor<TransactionHistory> captor = forClass(TransactionHistory.class);
        verify(transactionHistoryRepository, times(2)).save(captor.capture());
        List<TransactionHistory> histories = captor.getAllValues();
        TransactionHistory savingHistory = histories.get(0);
        assertThat(savingHistory.getType()).isEqualTo(TransactionType.SAVING_CANCEL_REFUND);
        assertThat(savingHistory.getDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(savingHistory.getAmount()).isEqualTo(1000000L);
        assertThat(savingHistory.getBalanceBefore()).isEqualTo(1000000L);
        assertThat(savingHistory.getBalanceAfter()).isZero();
        assertThat(savingHistory.getMemo()).isEqualTo("예금 중도 해지 출금");

        TransactionHistory history = histories.get(1);
        assertThat(history.getType()).isEqualTo(TransactionType.SAVING_CANCEL_REFUND);
        assertThat(history.getDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(history.getAmount()).isEqualTo(1008750L);
        assertThat(history.getBalanceBefore()).isEqualTo(2000000L);
        assertThat(history.getBalanceAfter()).isEqualTo(3008750L);
        assertThat(history.getMemo()).isEqualTo("예금 중도 해지 반환");
        verify(depositRepository).findByIdAndUserIdWithAccountForUpdate(1L, 1L);
    }

    @Test
    @DisplayName("가입중 적금을 중도 해지한다")
    void cancelInstallment() {
        Installment installment = createInstallment(1L, InstallmentStatus.ACTIVE);
        ReflectionTestUtils.setField(installment, "createdAt", LocalDateTime.of(2025, 12, 23, 0, 0));
        EarlyCancelReq request = new EarlyCancelReq(SavingProductType.INSTALLMENT);

        when(installmentRepository.findByIdAndUserIdWithAccountForUpdate(1L, 1L))
                .thenReturn(Optional.of(installment));

        EarlyCancelRes response = savingDepositService.cancelSaving(1L, 1L, request);

        assertThat(response.savingId()).isEqualTo(1L);
        assertThat(response.savingType()).isEqualTo(SavingProductType.INSTALLMENT);
        assertThat(response.principalAmount()).isEqualTo(100000L);
        assertThat(response.interestAmount()).isEqualTo(750L);
        assertThat(response.refundAmount()).isEqualTo(100750L);
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(activeAccount.getBalance()).isEqualTo(2100750L);
        assertThat(installment.getSavingAccount().getBalance()).isZero();
        assertThat(installment.getSavingAccount().getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.CANCELLED);

        ArgumentCaptor<TransactionHistory> captor = forClass(TransactionHistory.class);
        verify(transactionHistoryRepository, times(2)).save(captor.capture());
        List<TransactionHistory> histories = captor.getAllValues();
        TransactionHistory savingHistory = histories.get(0);
        assertThat(savingHistory.getType()).isEqualTo(TransactionType.SAVING_CANCEL_REFUND);
        assertThat(savingHistory.getDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(savingHistory.getAmount()).isEqualTo(100000L);
        assertThat(savingHistory.getBalanceBefore()).isEqualTo(100000L);
        assertThat(savingHistory.getBalanceAfter()).isZero();
        assertThat(savingHistory.getMemo()).isEqualTo("적금 중도 해지 출금");

        TransactionHistory history = histories.get(1);
        assertThat(history.getType()).isEqualTo(TransactionType.SAVING_CANCEL_REFUND);
        assertThat(history.getDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(history.getAmount()).isEqualTo(100750L);
        assertThat(history.getBalanceBefore()).isEqualTo(2000000L);
        assertThat(history.getBalanceAfter()).isEqualTo(2100750L);
        assertThat(history.getMemo()).isEqualTo("적금 중도 해지 반환");
        verify(installmentRepository).findByIdAndUserIdWithAccountForUpdate(1L, 1L);
    }

    @Test
    @DisplayName("가입중 상태가 아니면 중도 해지에 실패한다")
    void cancelSavingWithNotActiveStatus() {
        Deposit deposit = createDeposit(1L, DepositStatus.MATURED);
        EarlyCancelReq request = new EarlyCancelReq(SavingProductType.DEPOSIT);

        when(depositRepository.findByIdAndUserIdWithAccountForUpdate(1L, 1L))
                .thenReturn(Optional.of(deposit));

        assertThatThrownBy(() -> savingDepositService.cancelSaving(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.SAVING_CANCEL_NOT_ALLOWED);
    }

    @Test
    @DisplayName("출금 제한된 예금은 중도 해지에 실패한다")
    void cancelDepositWithWithdrawalLock() {
        Deposit deposit = createDeposit(1L, DepositStatus.ACTIVE);
        deposit.updateWithdrawalLock(true, "목표 저축을 위해 제한");
        EarlyCancelReq request = new EarlyCancelReq(SavingProductType.DEPOSIT);

        when(depositRepository.findByIdAndUserIdWithAccountForUpdate(1L, 1L))
                .thenReturn(Optional.of(deposit));

        assertThatThrownBy(() -> savingDepositService.cancelSaving(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.SAVING_WITHDRAWAL_LOCKED);

        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.ACTIVE);
        assertThat(activeAccount.getBalance()).isEqualTo(2000000L);
        verify(transactionHistoryRepository, never()).save(any(TransactionHistory.class));
    }

    @Test
    @DisplayName("출금 제한된 적금은 중도 해지에 실패한다")
    void cancelInstallmentWithWithdrawalLock() {
        Installment installment = createInstallment(1L, InstallmentStatus.ACTIVE);
        installment.updateWithdrawalLock(true, "목표 저축을 위해 제한");
        EarlyCancelReq request = new EarlyCancelReq(SavingProductType.INSTALLMENT);

        when(installmentRepository.findByIdAndUserIdWithAccountForUpdate(1L, 1L))
                .thenReturn(Optional.of(installment));

        assertThatThrownBy(() -> savingDepositService.cancelSaving(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.SAVING_WITHDRAWAL_LOCKED);

        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.ACTIVE);
        assertThat(activeAccount.getBalance()).isEqualTo(2000000L);
        verify(transactionHistoryRepository, never()).save(any(TransactionHistory.class));
    }

    @Test
    @DisplayName("만기일이 지난 가입중 예금을 만기 처리한다")
    void matureDeposit() {
        MaturityReq request = new MaturityReq(SavingProductType.DEPOSIT);
        MaturityRes maturityRes = new MaturityRes(
                1L,
                SavingProductType.DEPOSIT,
                1000000L,
                35000L,
                1035000L,
                "MATURED"
        );

        when(savingBatchProcessor.matureDeposit(1L, 1L))
                .thenReturn(maturityRes);

        MaturityRes response = savingDepositService.matureSaving(1L, 1L, request);

        assertThat(response).isEqualTo(maturityRes);
        verify(savingBatchProcessor).matureDeposit(1L, 1L);
    }

    @Test
    @DisplayName("만기일이 지난 가입중 적금을 만기 처리한다")
    void matureInstallment() {
        MaturityReq request = new MaturityReq(SavingProductType.INSTALLMENT);
        MaturityRes maturityRes = new MaturityRes(
                1L,
                SavingProductType.INSTALLMENT,
                100000L,
                19500L,
                119500L,
                "MATURED"
        );

        when(savingBatchProcessor.matureInstallment(1L, 1L))
                .thenReturn(maturityRes);

        MaturityRes response = savingDepositService.matureSaving(1L, 1L, request);

        assertThat(response).isEqualTo(maturityRes);
        verify(savingBatchProcessor).matureInstallment(1L, 1L);
    }

    @Test
    @DisplayName("가입중 상태가 아니면 만기 처리에 실패한다")
    void matureSavingWithNotActiveStatus() {
        MaturityReq request = new MaturityReq(SavingProductType.DEPOSIT);

        when(savingBatchProcessor.matureDeposit(1L, 1L))
                .thenThrow(new BusinessException(SavingErrorCode.SAVING_MATURITY_NOT_ALLOWED));

        assertThatThrownBy(() -> savingDepositService.matureSaving(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.SAVING_MATURITY_NOT_ALLOWED);
    }

    @Test
    @DisplayName("만기일이 아직 지나지 않으면 만기 처리에 실패한다")
    void matureSavingBeforeMaturityDate() {
        MaturityReq request = new MaturityReq(SavingProductType.DEPOSIT);

        when(savingBatchProcessor.matureDeposit(1L, 1L))
                .thenThrow(new BusinessException(SavingErrorCode.SAVING_NOT_MATURED_YET));

        assertThatThrownBy(() -> savingDepositService.matureSaving(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.SAVING_NOT_MATURED_YET);
    }

    @Test
    @DisplayName("만기 대상 예금과 적금을 일괄 만기 처리한다")
    void matureDueSavings() {
        when(depositRepository.findIdsByStatusAndMaturityDateLessThanEqual(
                DepositStatus.ACTIVE,
                TODAY
        )).thenReturn(List.of(1L));
        when(installmentRepository.findIdsByStatusAndMaturityDateLessThanEqual(
                InstallmentStatus.ACTIVE,
                TODAY
        )).thenReturn(List.of(1L));

        int maturedCount = savingDepositService.matureDueSavings();

        assertThat(maturedCount).isEqualTo(2);
        verify(savingBatchProcessor).matureDeposit(1L);
        verify(savingBatchProcessor).matureInstallment(1L);
    }

    @Test
    @DisplayName("정기 납입 대상 적금의 자동이체에 성공한다")
    void processDueInstallmentPayments() {
        when(installmentRepository.findPaymentTargetIds(
                InstallmentStatus.ACTIVE,
                TODAY
        )).thenReturn(List.of(1L));

        int processedCount = savingDepositService.processDueInstallmentPayments();

        assertThat(processedCount).isEqualTo(1);
        verify(savingBatchProcessor).processInstallmentPayment(1L);
    }

    @Test
    @DisplayName("정기 납입 자동이체 잔액이 부족하면 납입 실패 상태로 변경한다")
    void processDueInstallmentPaymentsWithInsufficientBalance() {
        when(installmentRepository.findPaymentTargetIds(
                InstallmentStatus.ACTIVE,
                TODAY
        )).thenReturn(List.of(1L));

        int processedCount = savingDepositService.processDueInstallmentPayments();

        assertThat(processedCount).isEqualTo(1);
        verify(savingBatchProcessor).processInstallmentPayment(1L);
    }

    @Test
    @DisplayName("목표 금액을 이미 채운 적금은 자동이체하지 않는다")
    void processDueInstallmentPaymentsWithReachedTargetAmount() {
        when(installmentRepository.findPaymentTargetIds(
                InstallmentStatus.ACTIVE,
                TODAY
        )).thenReturn(List.of(1L));

        int processedCount = savingDepositService.processDueInstallmentPayments();

        assertThat(processedCount).isEqualTo(1);
        verify(savingBatchProcessor).processInstallmentPayment(1L);
    }

    @Test
    @DisplayName("다음 납입일이 만기일 이상이면 자동이체하지 않는다")
    void processDueInstallmentPaymentsOnOrAfterMaturityDate() {
        when(installmentRepository.findPaymentTargetIds(
                InstallmentStatus.ACTIVE,
                TODAY
        )).thenReturn(List.of(1L));

        int processedCount = savingDepositService.processDueInstallmentPayments();

        assertThat(processedCount).isEqualTo(1);
        verify(savingBatchProcessor).processInstallmentPayment(1L);
    }

    @Test
    @DisplayName("출금 계좌가 비활성이면 자동이체하지 않고 납입 실패 상태로 변경한다")
    void processDueInstallmentPaymentsWithInactiveAccount() {
        when(installmentRepository.findPaymentTargetIds(
                InstallmentStatus.ACTIVE,
                TODAY
        )).thenReturn(List.of(1L));

        int processedCount = savingDepositService.processDueInstallmentPayments();

        assertThat(processedCount).isEqualTo(1);
        verify(savingBatchProcessor).processInstallmentPayment(1L);
    }

    @Test
    @DisplayName("실패한 적금 납입 재시도에 성공하면 ACTIVE 상태로 복구한다")
    void retryFailedInstallmentPayments() {
        when(installmentRepository.findRetryTargetIds(
                InstallmentStatus.PAYMENT_FAILED,
                TODAY
        )).thenReturn(List.of(1L));

        int retryCount = savingDepositService.retryFailedInstallmentPayments();

        assertThat(retryCount).isEqualTo(1);
        verify(savingBatchProcessor).processInstallmentPayment(1L);
    }

    @Test
    @DisplayName("실패한 적금 납입 재시도가 최대 횟수에 도달하면 다음 재시도일을 비운다")
    void retryFailedInstallmentPaymentsWithMaxRetryCount() {
        when(installmentRepository.findRetryTargetIds(
                InstallmentStatus.PAYMENT_FAILED,
                TODAY
        )).thenReturn(List.of(1L));

        int retryCount = savingDepositService.retryFailedInstallmentPayments();

        assertThat(retryCount).isEqualTo(1);
        verify(savingBatchProcessor).processInstallmentPayment(1L);
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
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 20L);
            return account;
        });
        when(installmentRepository.save(any(Installment.class))).thenAnswer(invocation -> {
            Installment installment = invocation.getArgument(0);
            ReflectionTestUtils.setField(installment, "id", 1L);
            return installment;
        });

        InstallmentCreateRes response = savingDepositService.createInstallment(1L, request);

        assertThat(response.installmentId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(InstallmentStatus.ACTIVE);
        assertThat(response.maturityDate()).isEqualTo(TODAY.plusMonths(12));
        assertThat(response.progressRate()).isEqualTo(8L);
        assertThat(activeAccount.getBalance()).isEqualTo(1900000L);

        ArgumentCaptor<Account> accountCaptor = forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savingAccount = accountCaptor.getValue();
        assertThat(savingAccount.getAccountType()).isEqualTo(AccountType.SAVING_INSTALLMENT);
        assertThat(savingAccount.getBalance()).isEqualTo(100000L);

        ArgumentCaptor<TransactionHistory> historyCaptor = forClass(TransactionHistory.class);
        verify(transactionHistoryRepository, times(2)).save(historyCaptor.capture());
        List<TransactionHistory> histories = historyCaptor.getAllValues();
        assertThat(histories.get(0).getType()).isEqualTo(TransactionType.SAVING_INSTALLMENT_SIGNUP);
        assertThat(histories.get(0).getDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(histories.get(0).getAmount()).isEqualTo(100000L);
        assertThat(histories.get(0).getBalanceBefore()).isEqualTo(2000000L);
        assertThat(histories.get(0).getBalanceAfter()).isEqualTo(1900000L);
        assertThat(histories.get(1).getType()).isEqualTo(TransactionType.SAVING_INSTALLMENT_SIGNUP);
        assertThat(histories.get(1).getDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(histories.get(1).getAmount()).isEqualTo(100000L);
        assertThat(histories.get(1).getBalanceBefore()).isZero();
        assertThat(histories.get(1).getBalanceAfter()).isEqualTo(100000L);

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
        when(accountRepository.findByIdAndUserIdForUpdate(999L, 1L)).thenReturn(Optional.empty());

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
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(closedAccount));

        assertThatThrownBy(() -> savingDepositService.createInstallment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("입출금계좌가 아닌 계좌로는 적금에 가입할 수 없다")
    void createInstallmentWithSavingWithdrawAccount() {
        InstallmentCreateReq request = new InstallmentCreateReq(2L, 1L, 100000L, 1200000L, true);
        Account savingAccount = createSavingAccount(1L, AccountType.SAVING_INSTALLMENT, 2000000L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(2L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.of(installmentProduct));
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(savingAccount));

        assertThatThrownBy(() -> savingDepositService.createInstallment(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_TRANSFER_OUT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("최소 월 납입액보다 작으면 적금 가입에 실패한다")
    void createInstallmentWithLessThanMinAmount() {
        InstallmentCreateReq request = new InstallmentCreateReq(2L, 1L, 5000L, 60000L, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(2L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.of(installmentProduct));
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(activeAccount));

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
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(activeAccount));

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
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(activeAccount));

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
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(insufficientAccount));

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
