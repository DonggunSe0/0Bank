package com.team10.backend.domain.saving.entity;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.saving.type.DepositStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "deposits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deposit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 가입 사용자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saving_product_id", nullable = false)
    private SavingProduct savingProduct; // 가입한 예금 상품

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdraw_account_id", nullable = false)
    private Account withdrawAccount; // 출금 계좌

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saving_account_id", nullable = false)
    private Account savingAccount; // 예금 전용 계좌

    @Column(nullable = false)
    private Long principal; // 예치 원금

    @Column(nullable = false)
    private Double interestRate; // 가입 당시 금리

    @Column(nullable = false)
    private LocalDate maturityDate; // 만기일

    @Column(nullable = false)
    private Long expectedInterest; // 예상 이자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepositStatus status; // 예금 상태

    public static Deposit create(
            User user,
            SavingProduct savingProduct,
            Account withdrawAccount,
            Account savingAccount,
            Long principal,
            Double interestRate,
            LocalDate maturityDate,
            Long expectedInterest
    ) {
        Deposit deposit = new Deposit();
        deposit.user = user;
        deposit.savingProduct = savingProduct;
        deposit.withdrawAccount = withdrawAccount;
        deposit.savingAccount = savingAccount;
        deposit.principal = principal;
        deposit.interestRate = interestRate;
        deposit.maturityDate = maturityDate;
        deposit.expectedInterest = expectedInterest;
        deposit.status = DepositStatus.ACTIVE;
        return deposit;
    }


    public void cancel() {
        this.status = DepositStatus.CANCELLED;
    }

    public void mature() {
        this.status = DepositStatus.MATURED;
    }
}
