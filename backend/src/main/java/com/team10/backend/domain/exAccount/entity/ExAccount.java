package com.team10.backend.domain.exAccount.entity;

import com.team10.backend.domain.exAccount.Type.ExAccountStatus;
import com.team10.backend.domain.exAccount.Type.ExAccountType;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "external_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/** CODEF 등 외부기관에서 가져온 사용자 외부 계좌의 최신 스냅샷을 저장한다. */
public class ExAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String organization; //금융기관코드

    @Column(nullable = false, length = 80)
    private String accountNumber;

    @Column(nullable = false, length = 100)
    private String accountName;

    @Column(length = 100)
    private String accountAlias;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private ExAccountType assetType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(precision = 19, scale = 2)
    private BigDecimal withdrawableAmount;

    private LocalDate openedAt;
    private LocalDate maturityAt;
    private LocalDate lastTransactionAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExAccountStatus status;

    public static ExAccount create(
            User user,
            String organization,
            String accountNumber,
            String accountName,
            String accountAlias,
            ExAccountType assetType,
            BigDecimal balance,
            BigDecimal withdrawableAmount,
            LocalDate openedAt,
            LocalDate maturityAt,
            LocalDate lastTransactionAt
    ) {
        ExAccount account = new ExAccount();
        account.user = user;
        account.organization = organization;
        account.accountNumber = accountNumber;
        account.accountName = accountName;
        account.accountAlias = accountAlias;
        account.assetType = assetType;
        account.balance = balance == null ? BigDecimal.ZERO : balance;
        account.withdrawableAmount = withdrawableAmount;
        account.openedAt = openedAt;
        account.maturityAt = maturityAt;
        account.lastTransactionAt = lastTransactionAt;
        account.status = ExAccountStatus.ACTIVE;
        return account;
    }

    public void updateSnapshot(String accountName, String accountAlias, BigDecimal balance,
                               BigDecimal withdrawableAmount, LocalDate maturityAt,
                               LocalDate lastTransactionAt) {
        this.accountName = accountName;
        this.accountAlias = accountAlias;
        this.balance = balance == null ? BigDecimal.ZERO : balance;
        this.withdrawableAmount = withdrawableAmount;
        this.maturityAt = maturityAt;
        this.lastTransactionAt = lastTransactionAt;
        this.status = ExAccountStatus.ACTIVE;
    }

    public void updateLastTransactionAt(LocalDate lastTransactionAt) {
        this.lastTransactionAt = lastTransactionAt;
        this.status = ExAccountStatus.ACTIVE;
    }

    public String getAccountNoMasked() {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }

        int prefixLength = Math.min(6, accountNumber.length() - 4);
        String prefix = accountNumber.substring(0, prefixLength);
        String suffix = accountNumber.substring(accountNumber.length() - 4);
        return prefix + "*".repeat(accountNumber.length() - prefixLength - 4) + suffix;
    }
}
