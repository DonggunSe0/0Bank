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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 가입 사용자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saving_product_id", nullable = false)
    private SavingProduct savingProduct; // 가입한 적금 상품

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdraw_account_id", nullable = false)
    private Account withdrawAccount; // 출금 계좌

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
    private boolean autoTransferYn; // 자동이체 여부

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentStatus status; // 적금 상태

    public static Installment create(
            User user,
            SavingProduct savingProduct,
            Account withdrawAccount,
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
        installment.monthlyAmount = monthlyAmount;
        installment.targetAmount = targetAmount;
        installment.paidAmount = monthlyAmount; // 가입 시 1회차 납입금
        installment.interestRate = interestRate;
        installment.maturityDate = maturityDate;
        installment.autoTransferYn = autoTransferYn;
        installment.status = InstallmentStatus.ACTIVE;
        return installment;
    }

    public Long getProgressRate() {
        return paidAmount * 100 / targetAmount;
    }
}
