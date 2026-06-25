package com.team10.backend.domain.exchange.entity;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.exchange.type.ExchangeDirection;
import com.team10.backend.domain.exchange.type.ExchangeOrderStatus;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "exchange_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_quote_id", nullable = false, unique = true)
    private ExchangeQuote exchangeQuote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "krw_account_id")
    private Account krwAccount; // 원화 계좌

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fx_wallet_id")
    private FxWallet fxWallet; // 외화 지갑

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private ExchangeDirection direction; // KRW_TO_FOREIGN: krwAccount에서 원화 차감 & fxWallet에 외화 증가

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_history_id")
    private TransactionHistory transactionHistory; // 원화 계좌 기록만 연결

    @Column(name = "from_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal toAmount;

    @Column(name = "applied_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal appliedRate;

    @Column(nullable = false, precision = 9, scale = 6) // 999.999999
    private BigDecimal feeRate; // 수수료율 (0.25%   -> 0.002500)

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee; // 최종 환전 수수료 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExchangeOrderStatus status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static ExchangeOrder createCompleted(
            User user,
            ExchangeQuote exchangeQuote,
            Account krwAccount,
            FxWallet fxWallet,
            ExchangeDirection direction,
            TransactionHistory transactionHistory,
            LocalDateTime completedAt
    ) {
        ExchangeOrder exchangeOrder = new ExchangeOrder();
        exchangeOrder.user = user;
        exchangeOrder.exchangeQuote = exchangeQuote;
        exchangeOrder.krwAccount = krwAccount;
        exchangeOrder.fxWallet = fxWallet;
        exchangeOrder.direction = direction;
        exchangeOrder.transactionHistory = transactionHistory;
        exchangeOrder.fromAmount = exchangeQuote.getFromAmount();
        exchangeOrder.toAmount = exchangeQuote.getExpectedToAmount();
        exchangeOrder.appliedRate = exchangeQuote.getRate();
        exchangeOrder.feeRate = exchangeQuote.getFeeRate();
        exchangeOrder.fee = exchangeQuote.getFee();
        exchangeOrder.status = ExchangeOrderStatus.COMPLETED;
        exchangeOrder.completedAt = completedAt;
        return exchangeOrder;
    }
}
