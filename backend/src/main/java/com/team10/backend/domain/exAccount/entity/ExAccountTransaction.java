package com.team10.backend.domain.exAccount.entity;

import com.team10.backend.domain.exAccount.Type.ExAccountTransactionDirection;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Entity
@Table(
        name = "external_asset_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_asset_transaction_account_key",
                columnNames = {"external_account_id", "transaction_key"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/** exAccount 계좌별 외부 거래 내역을 저장한다. */
public class ExAccountTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "external_account_id", nullable = false)
    private ExAccount exAccount;

    @Column(nullable = false, length = 160)
    private String transactionKey;

    @Column(nullable = false)
    private LocalDateTime transactedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExAccountTransactionDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 100)
    private String counterpartyName;

    @Column(length = 255)
    private String memo;

    @Column(length = 80)
    private String rawCategory;

    public static ExAccountTransaction create(ExAccount exAccount, String transactionKey,
                                              LocalDateTime transactedAt, ExAccountTransactionDirection direction,
                                              BigDecimal amount, BigDecimal balanceAfter, String counterpartyName,
                                              String memo, String rawCategory) {
        ExAccountTransaction transaction = new ExAccountTransaction();
        transaction.exAccount = exAccount;
        transaction.transactionKey = transactionKey;
        transaction.transactedAt = transactedAt;
        transaction.direction = direction;
        transaction.amount = amount == null ? BigDecimal.ZERO : amount;
        transaction.balanceAfter = balanceAfter;
        transaction.counterpartyName = counterpartyName;
        transaction.memo = memo;
        transaction.rawCategory = rawCategory;
        return transaction;
    }
}
