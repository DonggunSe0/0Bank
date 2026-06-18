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
@Table(
        name = "external_asset_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_asset_account_user_org_hash_type",
                columnNames = {"user_id", "organization", "account_no_hash", "asset_type"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/** CODEF 등 외부기관에서 가져온 사용자 외부 계좌의 최신 스냅샷을 저장한다. */
public class ExAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String organization;

    @Column(nullable = false, length = 80)
    private String accountNoMasked;

    @Column(nullable = false, length = 128)
    private String accountNoHash;

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
            String accountNoMasked,
            String accountNoHash,
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
        account.accountNoMasked = accountNoMasked;
        account.accountNoHash = accountNoHash;
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

    public void updateSnapshot(String accountNoMasked, String accountName, String accountAlias, BigDecimal balance,
                               BigDecimal withdrawableAmount, LocalDate openedAt, LocalDate maturityAt,
                               LocalDate lastTransactionAt) {
        this.accountNoMasked = accountNoMasked;
        this.accountName = accountName;
        this.accountAlias = accountAlias;
        this.balance = balance == null ? BigDecimal.ZERO : balance;
        this.withdrawableAmount = withdrawableAmount;
        this.openedAt = openedAt;
        this.maturityAt = maturityAt;
        this.lastTransactionAt = lastTransactionAt;
        this.status = ExAccountStatus.ACTIVE;
    }
}

