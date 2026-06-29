package com.team10.backend.domain.saving.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.service.AccountLockService;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.account.util.AccountNumberGenerator;
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
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavingDepositService {
    private static final int MONTHS_IN_YEAR = 12;
    private static final int PERCENT_DIVISOR = 100;
    private static final int EARLY_CANCEL_INTEREST_RATE_DIVISOR = 2;
    private static final int MAX_ACCOUNT_NUMBER_GENERATION_RETRY = 10;
    private static final String DEPOSIT_ACCOUNT_NICKNAME = "예금 계좌";
    private static final String INSTALLMENT_ACCOUNT_NICKNAME = "적금 계좌";
    private static final String DEPOSIT_SIGNUP_WITHDRAW_MEMO = "예금 가입 출금";
    private static final String DEPOSIT_SIGNUP_DEPOSIT_MEMO = "예금 계좌 입금";
    private static final String INSTALLMENT_SIGNUP_WITHDRAW_MEMO = "적금 가입 1회차 출금";
    private static final String INSTALLMENT_SIGNUP_DEPOSIT_MEMO = "적금 계좌 1회차 입금";
    private static final String DEPOSIT_CANCEL_REFUND_MEMO = "예금 중도 해지 반환";
    private static final String INSTALLMENT_CANCEL_REFUND_MEMO = "적금 중도 해지 반환";
    private static final String DEPOSIT_CANCEL_WITHDRAW_MEMO = "예금 중도 해지 출금";
    private static final String INSTALLMENT_CANCEL_WITHDRAW_MEMO = "적금 중도 해지 출금";

    private final TransactionHistoryRepository transactionHistoryRepository;
    private final DepositRepository depositRepository;
    private final SavingProductRepository savingProductRepository;
    private final AccountRepository accountRepository;
    private final AccountLockService accountLockService;
    private final UserRepository userRepository;
    private final InstallmentRepository installmentRepository;
    private final SavingBatchProcessor savingBatchProcessor;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public DepositCreateRes createDeposit(Long userId, DepositCreateReq request) {
        User user = findUser(userId);
        SavingProduct savingProduct = findActiveSavingProduct(request.productId(), SavingProductType.DEPOSIT);
        Account withdrawAccount = findActiveWithdrawAccountForUpdate(request.withdrawAccountId(), userId);

        validateDepositAmount(request.amount(), savingProduct);
        withdrawAccount.verifyPassword(passwordEncoder, request.accountPassword());

        LocalDate maturityDate = LocalDate.now(clock)
                .plusMonths(savingProduct.getPeriodMonth());

        Long withdrawBalanceBefore = withdrawAccount.getBalance();
        withdrawAccount.withdraw(request.amount());
        Long withdrawBalanceAfter = withdrawAccount.getBalance();

        Account savingAccount = createSavingAccount(
                user,
                DEPOSIT_ACCOUNT_NICKNAME,
                AccountType.SAVING_DEPOSIT,
                request.amount()
        );
        saveDepositSignupHistories(
                withdrawAccount,
                savingAccount,
                request.amount(),
                withdrawBalanceBefore,
                withdrawBalanceAfter
        );

        // 원금 × 연이율 × 가입개월수 ÷ 1년 개월수 ÷ 퍼센트 변환값
        Long expectedInterest = (long) (
                request.amount()
                        * savingProduct.getInterestRate()
                        * savingProduct.getPeriodMonth()
                        / MONTHS_IN_YEAR
                        / PERCENT_DIVISOR
        );

        Deposit deposit = Deposit.create(
                user,
                savingProduct,
                withdrawAccount,
                savingAccount,
                request.amount(),
                savingProduct.getInterestRate(),
                maturityDate,
                expectedInterest
        );

        Deposit savedDeposit = depositRepository.save(deposit);

        return DepositCreateRes.from(savedDeposit);
    }

    @Transactional
    public InstallmentCreateRes createInstallment(Long userId, InstallmentCreateReq request) {
        User user = findUser(userId);
        SavingProduct savingProduct = findActiveSavingProduct(request.productId(), SavingProductType.INSTALLMENT);
        Account withdrawAccount = findActiveWithdrawAccountForUpdate(request.withdrawAccountId(), userId);

        validateInstallmentAmount(request.monthlyAmount(), request.targetAmount(), savingProduct);
        withdrawAccount.verifyPassword(passwordEncoder, request.accountPassword());

        LocalDate maturityDate = LocalDate.now(clock)
                .plusMonths(savingProduct.getPeriodMonth());

        // 적금 가입할 때 출금 계좌에서 1회차 월 납입액을 빼는 코드
        Long withdrawBalanceBefore = withdrawAccount.getBalance();
        withdrawAccount.withdraw(request.monthlyAmount());
        Long withdrawBalanceAfter = withdrawAccount.getBalance();

        Account savingAccount = createSavingAccount(
                user,
                INSTALLMENT_ACCOUNT_NICKNAME,
                AccountType.SAVING_INSTALLMENT,
                request.monthlyAmount()
        );
        saveInstallmentSignupHistories(
                withdrawAccount,
                savingAccount,
                request.monthlyAmount(),
                withdrawBalanceBefore,
                withdrawBalanceAfter
        );

        Installment installment = Installment.create(
                user,
                savingProduct,
                withdrawAccount,
                savingAccount,
                request.monthlyAmount(),
                request.targetAmount(),
                savingProduct.getInterestRate(),
                maturityDate,
                request.autoTransferYn()
        );

        Installment savedInstallment = installmentRepository.save(installment);

        return InstallmentCreateRes.from(savedInstallment);
    }


    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.USER_NOT_FOUND));
    }

    private SavingProduct findActiveSavingProduct(Long productId, SavingProductType type) {
        return savingProductRepository.findByIdAndTypeAndActiveTrue(productId, type)
                .orElseThrow(() -> new BusinessException(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND));
    }

    private Account findActiveWithdrawAccountForUpdate(Long accountId, Long userId) {
        Account withdrawAccount = accountRepository.findByIdAndUserIdForUpdate(accountId, userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!withdrawAccount.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        if (!withdrawAccount.getAccountType().canTransferOut()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_TRANSFER_OUT_NOT_ALLOWED);
        }

        return withdrawAccount;
    }

    private void validateDepositAmount(Long amount, SavingProduct savingProduct) {
        if (amount < savingProduct.getMinAmount()) {
            throw new BusinessException(SavingErrorCode.INVALID_DEPOSIT_AMOUNT);
        }

        if (savingProduct.getMaxAmount() != null
                && amount > savingProduct.getMaxAmount()) {
            throw new BusinessException(SavingErrorCode.INVALID_DEPOSIT_AMOUNT);
        }
    }

    private void validateInstallmentAmount(
            Long monthlyAmount,
            Long targetAmount,
            SavingProduct savingProduct
    ) {
        if (monthlyAmount < savingProduct.getMinAmount()) {
            throw new BusinessException(SavingErrorCode.INVALID_INSTALLMENT_AMOUNT);
        }

        if (savingProduct.getMonthlyLimit() != null
                && monthlyAmount > savingProduct.getMonthlyLimit()) {
            throw new BusinessException(SavingErrorCode.INVALID_INSTALLMENT_AMOUNT);
        }

        Long expectedTargetAmount = monthlyAmount * savingProduct.getPeriodMonth();

        if (!targetAmount.equals(expectedTargetAmount)) {
            throw new BusinessException(SavingErrorCode.INVALID_TARGET_AMOUNT);
        }
    }

    public List<DepositSummaryRes> getDeposits(Long userId, DepositStatus status) {
        List<Deposit> deposits;

        if (status == null) {
            deposits = depositRepository.findAllByUserIdWithProduct(userId);
        } else {
            deposits = depositRepository.findAllByUserIdAndStatusWithProduct(userId, status);
        }

        return deposits.stream()
                .map(DepositSummaryRes::from)
                .toList();
    }

    public DepositDetailRes getDeposit(Long userId, Long depositId) {
        Deposit deposit =
                depositRepository.findByIdAndUserIdWithProduct(depositId, userId)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.DEPOSIT_NOT_FOUND));

        return DepositDetailRes.from(deposit);
    }

    public List<InstallmentSummaryRes> getInstallments(Long userId, InstallmentStatus status) {
        List<Installment> installments;

        if (status == null) {
            installments =
                    installmentRepository.findAllByUserIdWithProduct(userId);
        } else {
            installments =
                    installmentRepository.findAllByUserIdAndStatusWithProduct(userId, status);
        }

        return installments.stream()
                .map(InstallmentSummaryRes::from)
                .toList();
    }

    public InstallmentDetailRes getInstallment(Long userId, Long installmentId) {
        Installment installment =
                installmentRepository.findByIdAndUserIdWithProduct(installmentId, userId)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.INSTALLMENT_NOT_FOUND));

        return InstallmentDetailRes.from(installment);
    }

    public InterestPreviewRes getInterestPreview(
            Long userId,
            Long savingId,
            SavingProductType savingType
    ) {
        if (savingType == SavingProductType.DEPOSIT) {
            return getDepositInterestPreview(userId, savingId);
        }

        if (savingType == SavingProductType.INSTALLMENT) {
            return getInstallmentInterestPreview(userId, savingId);
        }

        throw new BusinessException(SavingErrorCode.INVALID_SAVING_TYPE);
    }

    @Transactional
    public EarlyCancelRes cancelSaving(
            Long userId,
            Long savingId,
            EarlyCancelReq request
    ) {
        if (request.savingType() == SavingProductType.DEPOSIT) {
            return cancelDeposit(userId, savingId);
        }

        if (request.savingType() == SavingProductType.INSTALLMENT) {
            return cancelInstallment(userId, savingId);
        }

        throw new BusinessException(SavingErrorCode.INVALID_SAVING_TYPE);
    }

    private InterestPreviewRes getDepositInterestPreview(Long userId, Long savingId) {
        Deposit deposit = findDepositWithProduct(userId, savingId);
        Long expectedTotalAmount = deposit.getPrincipal() + deposit.getExpectedInterest();

        return InterestPreviewRes.fromDeposit(deposit, expectedTotalAmount);
    }

    private InterestPreviewRes getInstallmentInterestPreview(Long userId, Long savingId) {
        Installment installment = findInstallmentWithProduct(userId, savingId);
        Long expectedInterest = calculateInstallmentExpectedInterest(installment);
        Long expectedTotalAmount = installment.getTargetAmount() + expectedInterest;

        return InterestPreviewRes.fromInstallment(installment, expectedInterest, expectedTotalAmount);
    }


    private EarlyCancelRes cancelDeposit(Long userId, Long savingId) {
        Deposit deposit = depositRepository.findByIdAndUserIdWithAccount(savingId, userId)
                .orElseThrow(() -> new BusinessException(SavingErrorCode.DEPOSIT_NOT_FOUND));

        validateDepositCancelable(deposit);

        Long interestAmount = calculateDepositEarlyCancelInterest(deposit);
        Long refundAmount = deposit.getPrincipal() + interestAmount;

        // 입출금 계좌와 예금 전용 계좌를 ID 작은 순서대로 잠근다.
        AccountLockService.LockedAccounts lockedAccounts =
                accountLockService.lockTwoAccounts(deposit.getWithdrawAccount(), deposit.getSavingAccount());

        // 락이 걸린 입출금 계좌를 꺼낸다.
        // 중도해지 환급금이 들어갈 계좌다.
        Account withdrawAccount = lockedAccounts.firstAccount();

        // 락이 걸린 예금 전용 계좌를 꺼낸다.
        // 중도해지 시 원금이 빠져나갈 계좌다.
        Account savingAccount = lockedAccounts.secondAccount();

        Long savingBalanceBefore = savingAccount.getBalance();

        closeSavingAccount(savingAccount, deposit.getPrincipal());
        Long savingBalanceAfter = savingAccount.getBalance();
        LocalDateTime transactedAt = LocalDateTime.now(clock);

        saveCancelWithdrawHistory(
                savingAccount,
                deposit.getPrincipal(),
                savingBalanceBefore,
                savingBalanceAfter,
                DEPOSIT_CANCEL_WITHDRAW_MEMO,
                transactedAt
        );
        saveCancelRefundHistory(withdrawAccount, refundAmount, DEPOSIT_CANCEL_REFUND_MEMO, transactedAt);
        deposit.cancel();

        return EarlyCancelRes.fromDeposit(deposit, interestAmount, refundAmount);
    }

    private EarlyCancelRes cancelInstallment(Long userId, Long savingId) {
        Installment installment = installmentRepository.findByIdAndUserIdWithAccount(savingId, userId)
                .orElseThrow(() -> new BusinessException(SavingErrorCode.INSTALLMENT_NOT_FOUND));

        validateInstallmentCancelable(installment);

        Long interestAmount = calculateInstallmentEarlyCancelInterest(installment);
        Long refundAmount = installment.getPaidAmount() + interestAmount;

        // 입출금 계좌와 적금 전용 계좌를 ID 작은 순서대로 잠근다.
        AccountLockService.LockedAccounts lockedAccounts =
                accountLockService.lockTwoAccounts(installment.getWithdrawAccount(),
                        installment.getSavingAccount());

        // 락이 걸린 입출금 계좌를 꺼낸다.
        // 중도해지 환급금이 들어갈 계좌다.
        Account withdrawAccount = lockedAccounts.firstAccount();

        // 락이 걸린 적금 전용 계좌를 꺼낸다.
        // 중도해지 시 납입금이 빠져나갈 계좌다.
        Account savingAccount = lockedAccounts.secondAccount();

        Long savingBalanceBefore = savingAccount.getBalance();

        closeSavingAccount(savingAccount, installment.getPaidAmount());
        Long savingBalanceAfter = savingAccount.getBalance();
        LocalDateTime transactedAt = LocalDateTime.now(clock);

        saveCancelWithdrawHistory(
                savingAccount,
                installment.getPaidAmount(),
                savingBalanceBefore,
                savingBalanceAfter,
                INSTALLMENT_CANCEL_WITHDRAW_MEMO,
                transactedAt
        );
        saveCancelRefundHistory(
                withdrawAccount,
                refundAmount,
                INSTALLMENT_CANCEL_REFUND_MEMO,
                transactedAt
        );
        installment.cancel();

        return EarlyCancelRes.fromInstallment(installment, interestAmount, refundAmount);
    }

    private Deposit findDepositWithProduct(Long userId, Long depositId) {
        return depositRepository.findByIdAndUserIdWithProduct(depositId, userId)
                .orElseThrow(() -> new BusinessException(SavingErrorCode.DEPOSIT_NOT_FOUND));
    }

    private Installment findInstallmentWithProduct(Long userId, Long installmentId) {
        return installmentRepository.findByIdAndUserIdWithProduct(installmentId, userId)
                .orElseThrow(() -> new BusinessException(SavingErrorCode.INSTALLMENT_NOT_FOUND));
    }

    private void validateDepositCancelable(Deposit deposit) {
        if (deposit.getStatus() != DepositStatus.ACTIVE) {
            throw new BusinessException(SavingErrorCode.SAVING_CANCEL_NOT_ALLOWED);
        }
    }

    private void validateInstallmentCancelable(Installment installment) {
        if (installment.getStatus() != InstallmentStatus.ACTIVE) {
            throw new BusinessException(SavingErrorCode.SAVING_CANCEL_NOT_ALLOWED);
        }
    }

    private void closeSavingAccount(Account savingAccount, Long amount) {
        savingAccount.withdraw(amount);
        if (!savingAccount.getBalance().equals(0L)) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_BALANCE_NOT_ZERO);
        }
        savingAccount.close();
    }

    private void saveDepositSignupHistories(
            Account withdrawAccount,
            Account savingAccount,
            Long amount,
            Long withdrawBalanceBefore,
            Long withdrawBalanceAfter
    ) {
        LocalDateTime transactedAt = LocalDateTime.now(clock);

        transactionHistoryRepository.save(TransactionHistory.createSavingDepositSignup(
                withdrawAccount,
                TransactionDirection.OUT,
                amount,
                withdrawBalanceBefore,
                withdrawBalanceAfter,
                DEPOSIT_SIGNUP_WITHDRAW_MEMO,
                transactedAt
        ));
        transactionHistoryRepository.save(TransactionHistory.createSavingDepositSignup(
                savingAccount,
                TransactionDirection.IN,
                amount,
                0L,
                savingAccount.getBalance(),
                DEPOSIT_SIGNUP_DEPOSIT_MEMO,
                transactedAt
        ));
    }

    private void saveInstallmentSignupHistories(
            Account withdrawAccount,
            Account savingAccount,
            Long amount,
            Long withdrawBalanceBefore,
            Long withdrawBalanceAfter
    ) {
        LocalDateTime transactedAt = LocalDateTime.now(clock);

        transactionHistoryRepository.save(TransactionHistory.createSavingInstallmentSignup(
                withdrawAccount,
                TransactionDirection.OUT,
                amount,
                withdrawBalanceBefore,
                withdrawBalanceAfter,
                INSTALLMENT_SIGNUP_WITHDRAW_MEMO,
                transactedAt
        ));
        transactionHistoryRepository.save(TransactionHistory.createSavingInstallmentSignup(
                savingAccount,
                TransactionDirection.IN,
                amount,
                0L,
                savingAccount.getBalance(),
                INSTALLMENT_SIGNUP_DEPOSIT_MEMO,
                transactedAt
        ));
    }

    private void saveCancelWithdrawHistory(
            Account savingAccount,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String memo,
            LocalDateTime transactedAt
    ) {
        TransactionHistory transactionHistory = TransactionHistory.createSavingCancelRefund(
                savingAccount,
                TransactionDirection.OUT,
                amount,
                balanceBefore,
                balanceAfter,
                memo,
                transactedAt
        );

        transactionHistoryRepository.save(transactionHistory);
    }

    private void saveCancelRefundHistory(
            Account withdrawAccount,
            Long refundAmount,
            String memo,
            LocalDateTime transactedAt
    ) {
        if (!withdrawAccount.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        Long balanceBefore = withdrawAccount.getBalance();
        withdrawAccount.deposit(refundAmount);
        Long balanceAfter = withdrawAccount.getBalance();

        TransactionHistory transactionHistory = TransactionHistory.createSavingCancelRefund(
                withdrawAccount,
                TransactionDirection.IN,
                refundAmount,
                balanceBefore,
                balanceAfter,
                memo,
                transactedAt
        );

        transactionHistoryRepository.save(transactionHistory);
    }

    private Account createSavingAccount(
            User user,
            String nickname,
            AccountType accountType,
            Long initialBalance
    ) {
        Account savingAccount = Account.create(
                user,
                generateUniqueAccountNumber(),
                nickname,
                accountType
        );
        savingAccount.deposit(initialBalance);

        return accountRepository.save(savingAccount);
    }

    private String generateUniqueAccountNumber() {
        for (int i = 0; i < MAX_ACCOUNT_NUMBER_GENERATION_RETRY; i++) {
            String accountNumber = AccountNumberGenerator.generate();

            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }

        throw new BusinessException(AccountErrorCode.ACCOUNT_NUMBER_GENERATION_FAILED);
    }

    private Long calculateDepositEarlyCancelInterest(Deposit deposit) {
        LocalDate startDate =
                deposit.getCreatedAt().toLocalDate(); // 예금 가입일

        LocalDate cancelDate =
                LocalDate.now(clock); // 중도 해지일

        long holdingMonths =
                ChronoUnit.MONTHS.between(startDate, cancelDate); // 가입일부터 해지일까지 지난 개월 수

        if (holdingMonths < 1) {
            holdingMonths = 1; // 최소 1개월 계산
        }

        return (long) (
                deposit.getPrincipal()          // 예치 원금
                        * deposit.getInterestRate() // 연 이율(%)
                        / PERCENT_DIVISOR       // 퍼센트를 소수로 변환
                        / MONTHS_IN_YEAR        // 연 이율을 월 이율로 변환
                        * holdingMonths         // 실제 보유 개월 수
                        / EARLY_CANCEL_INTEREST_RATE_DIVISOR // 중도 해지라 50%만 지급
        );
    }

    private Long calculateInstallmentEarlyCancelInterest(Installment installment) {
        LocalDate startDate =
                installment.getCreatedAt().toLocalDate(); // 적금 가입일

        LocalDate cancelDate =
                LocalDate.now(clock); // 중도 해지일

        long holdingMonths =
                ChronoUnit.MONTHS.between(startDate, cancelDate); // 가입일부터 해지일까지지난 개월 수

        if (holdingMonths < 1) {
            holdingMonths = 1; // 최소 1개월은 계산되도록 보정
        }

        return (long) (
                installment.getPaidAmount()        // 실제 납입한 금액
                        * installment.getInterestRate() // 연 이율(%)
                        / PERCENT_DIVISOR          // 퍼센트를 소수로 변환
                        / MONTHS_IN_YEAR           // 연 이율을 월 이율로 변환
                        * holdingMonths            // 실제 유지한 개월 수
                        / EARLY_CANCEL_INTEREST_RATE_DIVISOR // 중도 해지라서 이자의 50%만 지급
      );
    }

    private Long calculateInstallmentExpectedInterest(Installment installment) {
        int periodMonth =
                installment.getSavingProduct().getPeriodMonth(); // 적금 가입 기간(개월)

        return (long) (
                installment.getMonthlyAmount()      // 매월 납입하는 금액
                        * installment.getInterestRate() // 연 이율(%)
                        / PERCENT_DIVISOR          // 퍼센트를 소수로 변환
                        / MONTHS_IN_YEAR           // 연 이율을 월 이율로 변환
                        * periodMonth              // 가입 기간 개월 수
                        * (periodMonth + 1)        // 1부터 기간까지 합 계산용
                        / 2                        // 등차수열 합 공식
        );
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MaturityRes matureSaving(
            Long userId,
            Long savingId,
            MaturityReq request
    ) {
        if (request.savingType() == SavingProductType.DEPOSIT) {
            return savingBatchProcessor.matureDeposit(savingId, userId);
        }

        if (request.savingType() == SavingProductType.INSTALLMENT) {
            return savingBatchProcessor.matureInstallment(savingId, userId);
        }

        throw new BusinessException(SavingErrorCode.INVALID_SAVING_TYPE);
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int matureDueSavings() {
        LocalDate today = LocalDate.now(clock);

        List<Long> depositIds =

                depositRepository.findIdsByStatusAndMaturityDateLessThanEqual(
                        DepositStatus.ACTIVE,
                        today
                );

        List<Long> installmentIds =

                installmentRepository.findIdsByStatusAndMaturityDateLessThanEqual(
                        InstallmentStatus.ACTIVE,
                        today
                );

        for (Long depositId : depositIds) {
            try {
                savingBatchProcessor.matureDeposit(depositId);
            } catch (Exception e) {
                log.warn("예금 만기 처리 실패 depositId={}", depositId, e);
            }
        }

        for (Long installmentId : installmentIds) {
            try {
                savingBatchProcessor.matureInstallment(installmentId);
            } catch (Exception e) {
                log.warn("적금 만기 처리 실패 installmentId={}", installmentId, e);
            }
        }


        return depositIds.size() + installmentIds.size();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int processDueInstallmentPayments() {
        LocalDate today = LocalDate.now(clock);

        List<Long> installmentIds =
                installmentRepository.findPaymentTargetIds(
                        InstallmentStatus.ACTIVE,
                        today
                );

        for (Long installmentId : installmentIds) {
            try {
                savingBatchProcessor.processInstallmentPayment(installmentId);
            } catch (Exception e) {
                log.warn("적금 정기 납입 처리 실패 installmentId={}", installmentId, e);
            }
        }

        return installmentIds.size();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int retryFailedInstallmentPayments() {
        LocalDate today = LocalDate.now(clock);

        List<Long> installmentIds =
                installmentRepository.findRetryTargetIds(
                        InstallmentStatus.PAYMENT_FAILED,
                        today
                );

        for (Long installmentId : installmentIds) {
            try {
                savingBatchProcessor.processInstallmentPayment(installmentId);
            } catch (Exception e) {
                log.warn("적금 납입 재시도 처리 실패 installmentId={}", installmentId, e);
            }
        }

        return installmentIds.size();
    }

}
