package com.team10.backend.domain.account.entity;

import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transfer.errorcode.TransferErrorCode;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import com.team10.backend.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 30)
    private String accountNumber;

    @Column(length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType accountType;

    @Column(nullable = false)
    private Long balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    public static Account create(
            User user,
            String accountNumber,
            String nickname,
            AccountType accountType
    ) {
        Account account = new Account();
        account.user = user;
        account.accountNumber = accountNumber;
        account.nickname = nickname;
        account.accountType = accountType;
        account.balance = 0L;
        account.status = AccountStatus.ACTIVE;
        return account;
    }

    public void deposit(Long amount) {
        this.balance += amount;
    }

    public void withdraw(Long amount){
        if (balance - amount < 0){
            throw new BusinessException(TransferErrorCode.TRANSFER_FAILED);
        } else {
            this.balance -= amount;
        }
    }

    public boolean isActive(){
        return this.status.equals(AccountStatus.ACTIVE);
    }

}
