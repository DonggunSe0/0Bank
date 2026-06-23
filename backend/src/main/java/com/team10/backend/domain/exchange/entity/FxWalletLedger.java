package com.team10.backend.domain.exchange.entity;

import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "fx_wallet_ledgers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FxWalletLedger extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fx_wallet_id", nullable = false)
    private FxWallet fxWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_order_id")
    private ExchangeOrder exchangeOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code", referencedColumnName = "currency_code", nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private TransactionDirection direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "transacted_at", nullable = false)
    private LocalDateTime transactedAt;

    public static FxWalletLedger create(
            FxWallet fxWallet,
            ExchangeOrder exchangeOrder,
            Currency currency,
            TransactionDirection direction,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            LocalDateTime transactedAt
    ) {
        FxWalletLedger ledger = new FxWalletLedger();
        ledger.fxWallet = fxWallet;
        ledger.exchangeOrder = exchangeOrder;
        ledger.currency = currency;
        ledger.direction = direction;
        ledger.amount = amount;
        ledger.balanceBefore = balanceBefore;
        ledger.balanceAfter = balanceAfter;
        ledger.transactedAt = transactedAt;
        return ledger;
    }
}
