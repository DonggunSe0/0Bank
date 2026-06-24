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
import com.team10.backend.domain.transaction.type.TransactionDirection;
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
    private static final String DEPOSIT_MATURITY_WITHDRAW_MEMO = "예금 만기 출금";
    private static final String INSTALLMENT_MATURITY_WITHDRAW_MEMO = "적금 만기 출금";
    private static final String INSTALLMENT_PAYMENT_MEMO = "적금 월 납입 자동이체";
    private static final String INSTALLMENT_PAYMENT_DEPOSIT_MEMO = "적금 계좌 월 납입 입금";

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

        Account savingAccount = deposit.getSavingAccount();
        Long savingBalanceBefore = savingAccount.getBalance();
        closeSavingAccount(savingAccount, deposit.getPrincipal());
        Long savingBalanceAfter = savingAccount.getBalance();
        LocalDateTime transactedAt = LocalDateTime.now(clock);

        saveMaturityWithdrawHistory(
                savingAccount,
                deposit.getPrincipal(),
                savingBalanceBefore,
                savingBalanceAfter,
                DEPOSIT_MATURITY_WITHDRAW_MEMO,
                transactedAt
        );
        saveMaturityPayoutHistory(deposit.getWithdrawAccount(), payoutAmount, DEPOSIT_MATURITY_PAYOUT_MEMO, transactedAt);
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

        Account savingAccount = installment.getSavingAccount();
        Long savingBalanceBefore = savingAccount.getBalance();
        closeSavingAccount(savingAccount, installment.getPaidAmount());
        Long savingBalanceAfter = savingAccount.getBalance();
        LocalDateTime transactedAt = LocalDateTime.now(clock);

        saveMaturityWithdrawHistory(
                savingAccount,
                installment.getPaidAmount(),
                savingBalanceBefore,
                savingBalanceAfter,
                INSTALLMENT_MATURITY_WITHDRAW_MEMO,
                transactedAt
        );
        saveMaturityPayoutHistory(
                installment.getWithdrawAccount(),
                payoutAmount,
                INSTALLMENT_MATURITY_PAYOUT_MEMO,
                transactedAt
        );
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
        Account savingAccount = installment.getSavingAccount();
        Long savingBalanceBefore = savingAccount.getBalance();

        withdrawAccount.withdraw(paymentAmount);
        savingAccount.deposit(paymentAmount);

        Long balanceAfter = withdrawAccount.getBalance();
        Long savingBalanceAfter = savingAccount.getBalance();
        LocalDateTime transactedAt = LocalDateTime.now(clock);

        TransactionHistory transactionHistory = TransactionHistory.createInstallmentPayment(
                withdrawAccount,
                TransactionDirection.OUT,
                paymentAmount,
                balanceBefore,
                balanceAfter,
                INSTALLMENT_PAYMENT_MEMO,
                transactedAt
        );
        TransactionHistory savingAccountHistory = TransactionHistory.createInstallmentPayment(
                savingAccount,
                TransactionDirection.IN,
                paymentAmount,
                savingBalanceBefore,
                savingBalanceAfter,
                INSTALLMENT_PAYMENT_DEPOSIT_MEMO,
                transactedAt
        );

        transactionHistoryRepository.save(transactionHistory);
        transactionHistoryRepository.save(savingAccountHistory);
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

    private void saveMaturityWithdrawHistory(
            Account savingAccount,
            Long amount,
            Long balanceBefore,
            Long balanceAfter,
            String memo,
            LocalDateTime transactedAt
    ) {
        TransactionHistory transactionHistory = TransactionHistory.createSavingMaturityPayout(
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

    private void saveMaturityPayoutHistory(
            Account withdrawAccount,
            Long payoutAmount,
            String memo,
            LocalDateTime transactedAt
    ) {
        if (!withdrawAccount.isActive()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        Long balanceBefore = withdrawAccount.getBalance();
        withdrawAccount.deposit(payoutAmount);
        Long balanceAfter = withdrawAccount.getBalance();

        TransactionHistory transactionHistory = TransactionHistory.createSavingMaturityPayout(
                withdrawAccount,
                TransactionDirection.IN,
                payoutAmount,
                balanceBefore,
                balanceAfter,
                memo,
                transactedAt
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
