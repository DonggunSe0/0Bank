package com.team10.backend.domain.saving.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
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
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private static final String DEPOSIT_CANCEL_REFUND_MEMO = "예금 중도 해지 반환";
    private static final String INSTALLMENT_CANCEL_REFUND_MEMO = "적금 중도 해지 반환";

    private final TransactionHistoryRepository transactionHistoryRepository;
    private final DepositRepository depositRepository;
    private final SavingProductRepository savingProductRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final InstallmentRepository installmentRepository;
    private final SavingBatchProcessor savingBatchProcessor;
    private final Clock clock;

    @Transactional
    public DepositCreateRes createDeposit(Long userId, DepositCreateReq request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.USER_NOT_FOUND));

        SavingProduct savingProduct = savingProductRepository
                .findByIdAndTypeAndActiveTrue(request.productId(),
                        SavingProductType.DEPOSIT)
                .orElseThrow(() -> new BusinessException(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND));

        Account withdrawAccount = accountRepository
                .findByIdAndUserId(request.withdrawAccountId(), userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!withdrawAccount.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        if (request.amount() < savingProduct.getMinAmount()) {
            throw new BusinessException(SavingErrorCode.INVALID_DEPOSIT_AMOUNT);
        }

        if (savingProduct.getMaxAmount() != null
                && request.amount() > savingProduct.getMaxAmount()) {
            throw new BusinessException(SavingErrorCode.INVALID_DEPOSIT_AMOUNT);
        }

        LocalDate maturityDate = LocalDate.now(clock)
                .plusMonths(savingProduct.getPeriodMonth());

        withdrawAccount.withdraw(request.amount());

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.USER_NOT_FOUND));

        SavingProduct savingProduct = savingProductRepository
                .findByIdAndTypeAndActiveTrue(request.productId(),
                        SavingProductType.INSTALLMENT)
                .orElseThrow(() -> new BusinessException(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND));

        Account withdrawAccount = accountRepository
                .findByIdAndUserId(request.withdrawAccountId(), userId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!withdrawAccount.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        if (request.monthlyAmount() < savingProduct.getMinAmount()) {
            throw new BusinessException(SavingErrorCode.INVALID_INSTALLMENT_AMOUNT);
        }

        if (savingProduct.getMonthlyLimit() != null
                && request.monthlyAmount() > savingProduct.getMonthlyLimit()) {
            throw new BusinessException(SavingErrorCode.INVALID_INSTALLMENT_AMOUNT);
        }

        Long expectedTargetAmount =
                request.monthlyAmount() * savingProduct.getPeriodMonth();

        if (!request.targetAmount().equals(expectedTargetAmount)) {
            throw new BusinessException(SavingErrorCode.INVALID_TARGET_AMOUNT);
        }

        LocalDate maturityDate = LocalDate.now(clock)
                .plusMonths(savingProduct.getPeriodMonth());

        // 적금 가입할 때 출금 계좌에서 1회차 월 납입액을 빼는 코드
        withdrawAccount.withdraw(request.monthlyAmount());

        Installment installment = Installment.create(
                user,
                savingProduct,
                withdrawAccount,
                request.monthlyAmount(),
                request.targetAmount(),
                savingProduct.getInterestRate(),
                maturityDate,
                request.autoTransferYn()
        );

        Installment savedInstallment = installmentRepository.save(installment);

        return InstallmentCreateRes.from(savedInstallment);
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
            // 예금 조회
            Deposit deposit = depositRepository.findByIdAndUserIdWithProduct(savingId, userId)
                    .orElseThrow(() -> new
                            BusinessException(SavingErrorCode.DEPOSIT_NOT_FOUND));
            // 총 수령액 계산
            Long expectedTotalAmount = deposit.getPrincipal() + deposit.getExpectedInterest();

            return InterestPreviewRes.fromDeposit(deposit, expectedTotalAmount);
        }

        if (savingType == SavingProductType.INSTALLMENT) {
            // 적금 조회
            Installment installment = installmentRepository.findByIdAndUserIdWithProduct(savingId, userId)
                    .orElseThrow(() -> new
                            BusinessException(SavingErrorCode.INSTALLMENT_NOT_FOUND));

            Long expectedInterest = calculateInstallmentExpectedInterest(installment);

            Long expectedTotalAmount =
                    installment.getTargetAmount() + expectedInterest; // 만기 예상 수령액 = 목표 금액 + 예상 이자


            return InterestPreviewRes.fromInstallment(installment, expectedInterest, expectedTotalAmount);
        }

        // 이상한 타입이면 예외
        throw new BusinessException(SavingErrorCode.INVALID_SAVING_TYPE);
    }

    @Transactional
    public WithdrawalLockRes updateWithdrawalLock(
            Long userId,
            Long savingId,
            WithdrawalLockReq request
    ) {
        if (Boolean.FALSE.equals(request.lockYn()) && (request.reason() == null ||
                request.reason().isBlank())) {

            throw new
                    BusinessException(SavingErrorCode.WITHDRAWAL_UNLOCK_REASON_REQUIRED);
        }

        if (request.savingType() == SavingProductType.DEPOSIT) {
            Deposit deposit = depositRepository.findByIdAndUserIdWithProduct(savingId,
                            userId)
                    .orElseThrow(() -> new
                            BusinessException(SavingErrorCode.DEPOSIT_NOT_FOUND));

            deposit.updateWithdrawalLock(request.lockYn(), request.reason());

            return WithdrawalLockRes.fromDeposit(deposit);
        }

        if (request.savingType() == SavingProductType.INSTALLMENT) {
            Installment installment =
                    installmentRepository.findByIdAndUserIdWithProduct(savingId, userId)
                            .orElseThrow(() -> new
                                    BusinessException(SavingErrorCode.INSTALLMENT_NOT_FOUND));

            installment.updateWithdrawalLock(request.lockYn(), request.reason());

            return WithdrawalLockRes.fromInstallment(installment);
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
            Deposit deposit =
                    depositRepository.findByIdAndUserIdWithAccountForUpdate(savingId, userId)
                            .orElseThrow(() -> new
                                    BusinessException(SavingErrorCode.DEPOSIT_NOT_FOUND));

            if (deposit.getStatus() != DepositStatus.ACTIVE) {
                throw new
                        BusinessException(SavingErrorCode.SAVING_CANCEL_NOT_ALLOWED);
            }

            if (deposit.isWithdrawalLocked()) {
                throw new BusinessException(SavingErrorCode.SAVING_WITHDRAWAL_LOCKED);
            }

            Long interestAmount =
                    calculateDepositEarlyCancelInterest(deposit);

            Long refundAmount =
                    deposit.getPrincipal() + interestAmount;

            Account withdrawAccount = deposit.getWithdrawAccount(); // 반환금을 받을 연결 계좌
            Long balanceBefore = withdrawAccount.getBalance(); // 입금 전 계좌 잔액

            withdrawAccount.deposit(refundAmount); // 중도 해지 반환금 입금

            Long balanceAfter = withdrawAccount.getBalance(); // 입금 후 계좌 잔액

            TransactionHistory transactionHistory =
                    TransactionHistory.createSavingCancelRefund(
                            withdrawAccount,
                            refundAmount,
                            balanceBefore,
                            balanceAfter,
                            DEPOSIT_CANCEL_REFUND_MEMO,
                            LocalDateTime.now(clock)
                    );

            transactionHistoryRepository.save(transactionHistory);

            deposit.cancel();

            return EarlyCancelRes.fromDeposit(deposit, interestAmount, refundAmount);
        }

        if (request.savingType() == SavingProductType.INSTALLMENT) {
            Installment installment =
                    installmentRepository.findByIdAndUserIdWithAccountForUpdate(savingId,
                                    userId)
                            .orElseThrow(() -> new
                                    BusinessException(SavingErrorCode.INSTALLMENT_NOT_FOUND)
                            );

            if (installment.getStatus() != InstallmentStatus.ACTIVE) {
                throw new
                        BusinessException(SavingErrorCode.SAVING_CANCEL_NOT_ALLOWED);
            }

            if (installment.isWithdrawalLocked()) {
                throw new BusinessException(SavingErrorCode.SAVING_WITHDRAWAL_LOCKED);
            }

            Long interestAmount = calculateInstallmentEarlyCancelInterest(installment);

            Long refundAmount =
                    installment.getPaidAmount() + interestAmount;

            Account withdrawAccount = installment.getWithdrawAccount(); // 반환금을 받을 연결 계좌
            Long balanceBefore = withdrawAccount.getBalance(); // 입금 전 계좌 잔액

            withdrawAccount.deposit(refundAmount); // 중도 해지 반환금 입금

            Long balanceAfter = withdrawAccount.getBalance(); // 입금 후 계좌 잔액

            TransactionHistory transactionHistory =
                    TransactionHistory.createSavingCancelRefund(
                            withdrawAccount,
                            refundAmount,
                            balanceBefore,
                            balanceAfter,
                            INSTALLMENT_CANCEL_REFUND_MEMO,
                            LocalDateTime.now(clock)
                    );

            transactionHistoryRepository.save(transactionHistory);

            installment.cancel();

            return EarlyCancelRes.fromInstallment(installment, interestAmount, refundAmount);
        }

        throw new BusinessException(SavingErrorCode.INVALID_SAVING_TYPE);
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
