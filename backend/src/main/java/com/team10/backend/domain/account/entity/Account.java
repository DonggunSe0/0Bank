package com.team10.backend.domain.account.entity;

import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.type.AccountStatus;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import com.team10.backend.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

@Getter
@Entity
@Table(name = "accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 계좌 소유 사용자

    @Column(nullable = false, unique = true, length = 30)
    private String accountNumber; // 계좌번호

    @Column(length = 50)
    private String nickname; // 계좌 별칭

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType accountType; // 계좌 타입

    @Column(nullable = false)
    private Long balance; // 계좌 잔액

    @Column(length = 255)
    private String accountPasswordHash; // 계좌 비밀번호 해시

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status; // 계좌 상태

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

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePassword(String accountPasswordHash) {
        this.accountPasswordHash = accountPasswordHash;
    }

    public void verifyPassword(PasswordEncoder encoder, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_PASSWORD_MISMATCH);
        }

        if (accountPasswordHash == null) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_PASSWORD_NOT_SET);
        }

        if (!encoder.matches(rawPassword, accountPasswordHash)) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_PASSWORD_MISMATCH);
        }
    }

    public void close() {
        this.status = AccountStatus.CLOSED;
    }

    public void deposit(Long amount) {
        this.balance += amount;
    }

    public void withdraw(Long amount) {
        if (balance < amount) {
            throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
        }

        this.balance -= amount;
    }

    public boolean isActive(){
        return this.status.equals(AccountStatus.ACTIVE);
    }

}
