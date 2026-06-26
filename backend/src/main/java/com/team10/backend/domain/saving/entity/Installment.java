package com.team10.backend.domain.saving.entity;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.saving.type.InstallmentStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "installments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Installment extends BaseEntity {

    private static final int MAX_PAYMENT_RETRY_COUNT = 3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 가입 사용자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saving_product_id", nullable = false)
    private SavingProduct savingProduct; // 가입한 적금 상품

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdraw_account_id", nullable = false)
    private Account withdrawAccount; // 출금 계좌

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saving_account_id", nullable = false)
    private Account savingAccount; // 적금 전용 계좌

    @Column(nullable = false)
    private Long monthlyAmount; // 매달 납입할 금액

    @Column(nullable = false)
    private Long targetAmount; // 목표 금액

    @Column(nullable = false)
    private Long paidAmount; // 현재까지 납입한 금액

    @Column(nullable = false)
    private Double interestRate; // 가입 당시 금리

    @Column(nullable = false)
    private LocalDate maturityDate; // 만기일

    @Column(nullable = false)
    private LocalDate nextPaymentDate; // 다음 정기 납입일

    @Column(nullable = false)
    private boolean autoTransferYn; // 자동이체 여부

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentStatus status; // 적금 상태

    @Column(nullable = false)
    private int paymentRetryCount; // 자동이체 실패/재시도 횟수

    @Column
    private LocalDate nextPaymentRetryDate; // 다음 자동이체 재시도일

    @Column
    private LocalDate lastPaymentFailedDate; // 마지막 자동이체 실패일

    @Column(length = 255)
    private String paymentFailureReason; // 자동이체 실패 사유

    public static Installment create(
            User user,
            SavingProduct savingProduct,
            Account withdrawAccount,
            Account savingAccount,
            Long monthlyAmount,
            Long targetAmount,
            Double interestRate,
            LocalDate maturityDate,
            boolean autoTransferYn
    ) {
        Installment installment = new Installment();
        installment.user = user;
        installment.savingProduct = savingProduct;
        installment.withdrawAccount = withdrawAccount;
        installment.savingAccount = savingAccount;
        installment.monthlyAmount = monthlyAmount;
        installment.targetAmount = targetAmount;
        installment.paidAmount = monthlyAmount; // 가입 시 1회차 납입금
        installment.nextPaymentDate = LocalDate.now().plusMonths(1);
        installment.interestRate = interestRate;
        installment.maturityDate = maturityDate;
        installment.autoTransferYn = autoTransferYn;
        installment.status = InstallmentStatus.ACTIVE;
        installment.paymentRetryCount = 0;
        installment.nextPaymentRetryDate = null;
        installment.lastPaymentFailedDate = null;
        installment.paymentFailureReason = null;
        return installment;
    }

    public Long getProgressRate() {
        return paidAmount * 100 / targetAmount;
    }


    public void payMonthlyAmount() {
        this.paidAmount += this.monthlyAmount;
        this.nextPaymentDate = this.nextPaymentDate.plusMonths(1);
        this.status = InstallmentStatus.ACTIVE;
        this.paymentRetryCount = 0;
        this.nextPaymentRetryDate = null;
        this.lastPaymentFailedDate = null;
        this.paymentFailureReason = null;
    }

    public void failPayment(String reason, LocalDate failedDate) {
        this.status = InstallmentStatus.PAYMENT_FAILED;
        this.paymentRetryCount += 1;
        this.lastPaymentFailedDate = failedDate;
        this.paymentFailureReason = reason;

        if (this.paymentRetryCount < MAX_PAYMENT_RETRY_COUNT) {
            this.nextPaymentRetryDate = failedDate.plusDays(1);
        } else {
            this.nextPaymentRetryDate = null;
        }
    }

    public void cancel() {
        this.status = InstallmentStatus.CANCELLED;
    }

    public void mature() {
        this.status = InstallmentStatus.MATURED;
    }
}
