package com.team10.backend.domain.saving.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.saving.dto.res.MaturityRes;
import com.team10.backend.domain.saving.entity.Deposit;
import com.team10.backend.domain.saving.entity.Installment;
import com.team10.backend.domain.saving.exception.SavingErrorCode;
import com.team10.backend.domain.saving.repository.DepositRepository;
import com.team10.backend.domain.saving.repository.InstallmentRepository;
import com.team10.backend.domain.saving.type.DepositStatus;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SavingBatchProcessor {
    private static final int MONTHS_IN_YEAR = 12;
    private static final int PERCENT_DIVISOR = 100;
    private static final String DEPOSIT_MATURITY_PAYOUT_MEMO = "예금 만기 지급";
    private static final String INSTALLMENT_MATURITY_PAYOUT_MEMO = "적금 만기 지급";
    private static final String INSTALLMENT_PAYMENT_MEMO = "적금 월 납입 자동이체";

    private final TransactionHistoryRepository transactionHistoryRepository;
    private final DepositRepository depositRepository;
    private final InstallmentRepository installmentRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processInstallmentPayment(Long installmentId) {
        Installment installment =
                installmentRepository.findByIdWithAccountForUpdate(installmentId)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.INSTALLMENT_NOT_FOUND));

        processInstallmentPayment(installment);
    }

    private void processInstallmentPayment(Installment installment) {
        LocalDate today = LocalDate.now(clock);

        if (!isInstallmentPaymentDue(installment, today)) {
            return;
        }

        Account withdrawAccount = installment.getWithdrawAccount();

        if (!withdrawAccount.isActive()) {
            failInstallmentPayment(installment, "출금 계좌 비활성", today);
            return;
        }

        if (withdrawAccount.getBalance() < installment.getMonthlyAmount()) {
            failInstallmentPayment(installment, "잔액 부족", today);
            return;
        }

        payInstallment(installment, withdrawAccount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MaturityRes matureDeposit(Long depositId, Long userId) {
        Deposit deposit =
                depositRepository.findByIdAndUserIdWithAccountForUpdate(depositId, userId)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.DEPOSIT_NOT_FOUND));

        return matureDeposit(deposit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MaturityRes matureDeposit(Long depositId) {
        Deposit deposit = depositRepository.findByIdWithAccountForUpdate(depositId)
                .orElseThrow(() -> new
                        BusinessException(SavingErrorCode.DEPOSIT_NOT_FOUND));

        return matureDeposit(deposit);
    }

    private MaturityRes matureDeposit(Deposit deposit) {
        validateDepositMaturityAllowed(deposit);

        Long interestAmount = deposit.getExpectedInterest();
        Long payoutAmount = deposit.getPrincipal() + interestAmount;

        closeSavingAccount(deposit.getSavingAccount(), deposit.getPrincipal());
        saveMaturityPayoutHistory(deposit.getWithdrawAccount(), payoutAmount, DEPOSIT_MATURITY_PAYOUT_MEMO);
        deposit.mature();

        return MaturityRes.fromDeposit(deposit, interestAmount, payoutAmount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MaturityRes matureInstallment(Long installmentId, Long userId) {
        Installment installment =
                installmentRepository.findByIdAndUserIdWithAccountForUpdate(installmentId,
                                userId)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.INSTALLMENT_NOT_FOUND));

        return matureInstallment(installment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MaturityRes matureInstallment(Long installmentId) {
        Installment installment =
                installmentRepository.findByIdWithAccountForUpdate(installmentId)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.INSTALLMENT_NOT_FOUND));

        return matureInstallment(installment);
    }

    private MaturityRes matureInstallment(Installment installment) {
        validateInstallmentMaturityAllowed(installment);

        Long interestAmount = calculateInstallmentExpectedInterest(installment);
        Long payoutAmount = installment.getPaidAmount() + interestAmount;

        closeSavingAccount(installment.getSavingAccount(), installment.getPaidAmount());
        saveMaturityPayoutHistory(installment.getWithdrawAccount(), payoutAmount, INSTALLMENT_MATURITY_PAYOUT_MEMO);
        installment.mature();

        return MaturityRes.fromInstallment(installment, interestAmount, payoutAmount);
    }

    private boolean isInstallmentPaymentDue(Installment installment, LocalDate today) {
        if (installment.getStatus() != InstallmentStatus.ACTIVE
                && installment.getStatus() != InstallmentStatus.PAYMENT_FAILED) {
            return false;
        }

        if (installment.getStatus() == InstallmentStatus.ACTIVE
                && installment.getNextPaymentDate().isAfter(today)) {
            return false;
        }

        if (installment.getStatus() == InstallmentStatus.PAYMENT_FAILED
                && (installment.getNextPaymentRetryDate() == null
                || installment.getNextPaymentRetryDate().isAfter(today))) {
            return false;
        }

        return installment.getPaidAmount() < installment.getTargetAmount()
                && installment.getNextPaymentDate().isBefore(installment.getMaturityDate());
    }

    private void failInstallmentPayment(Installment installment, String reason, LocalDate failedDate) {
        installment.failPayment(reason, failedDate);
    }

    private void payInstallment(Installment installment, Account withdrawAccount) {
        Long paymentAmount = installment.getMonthlyAmount();
        Long balanceBefore = withdrawAccount.getBalance();

        withdrawAccount.withdraw(paymentAmount);
        installment.getSavingAccount().deposit(paymentAmount);

        Long balanceAfter = withdrawAccount.getBalance();

        TransactionHistory transactionHistory = TransactionHistory.createInstallmentPayment(
                withdrawAccount,
                paymentAmount,
                balanceBefore,
                balanceAfter,
                INSTALLMENT_PAYMENT_MEMO,
                LocalDateTime.now(clock)
        );

        transactionHistoryRepository.save(transactionHistory);
        installment.payMonthlyAmount();
    }

    private void validateDepositMaturityAllowed(Deposit deposit) {
        if (deposit.getStatus() != DepositStatus.ACTIVE) {
            throw new BusinessException(SavingErrorCode.SAVING_MATURITY_NOT_ALLOWED);
        }

        if (deposit.getMaturityDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessException(SavingErrorCode.SAVING_NOT_MATURED_YET);
        }
    }

    private void validateInstallmentMaturityAllowed(Installment installment) {
        if (installment.getStatus() != InstallmentStatus.ACTIVE) {
            throw new BusinessException(SavingErrorCode.SAVING_MATURITY_NOT_ALLOWED);
        }

        if (installment.getMaturityDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessException(SavingErrorCode.SAVING_NOT_MATURED_YET);
        }
    }

    private void closeSavingAccount(Account savingAccount, Long amount) {
        savingAccount.withdraw(amount);
        savingAccount.close();
    }

    private void saveMaturityPayoutHistory(Account withdrawAccount, Long payoutAmount, String memo) {
        if (!withdrawAccount.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        Long balanceBefore = withdrawAccount.getBalance();
        withdrawAccount.deposit(payoutAmount);
        Long balanceAfter = withdrawAccount.getBalance();

        TransactionHistory transactionHistory = TransactionHistory.createSavingMaturityPayout(
                withdrawAccount,
                payoutAmount,
                balanceBefore,
                balanceAfter,
                memo,
                LocalDateTime.now(clock)
        );

        transactionHistoryRepository.save(transactionHistory);
    }

    private Long calculateInstallmentExpectedInterest(Installment installment) {
        int periodMonth =
                installment.getSavingProduct().getPeriodMonth();

        return (long) (
                installment.getMonthlyAmount()
                        * installment.getInterestRate()
                        / PERCENT_DIVISOR
                        / MONTHS_IN_YEAR
                        * periodMonth
                        * (periodMonth + 1)
                        / 2
        );
    }
}
