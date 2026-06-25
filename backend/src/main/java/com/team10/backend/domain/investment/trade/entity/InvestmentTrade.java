package com.team10.backend.domain.investment.trade.entity;

import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.trade.type.InvestmentTradeType;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "investment_trades",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_investment_trades_account_idempotency",
                columnNames = {"investment_account_id", "idempotency_key"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestmentTrade extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_account_id", nullable = false)
    private InvestmentAccount investmentAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvestmentTradeType tradeType;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false)
    private Long executionPrice;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long requestedPrice;

    @Column(nullable = false)
    private Integer priceDeviationBps;

    @Column(nullable = false)
    private Instant snapshotAt;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant executedAt;

    public static InvestmentTrade create(
            InvestmentAccount investmentAccount,
            Stock stock,
            InvestmentTradeType tradeType,
            Long quantity,
            Long executionPrice,
            Long totalAmount,
            Long requestedPrice,
            Integer priceDeviationBps,
            Instant snapshotAt,
            String idempotencyKey,
            Instant executedAt
    ) {
        InvestmentTrade trade = new InvestmentTrade();
        trade.investmentAccount = investmentAccount;
        trade.stock = stock;
        trade.tradeType = tradeType;
        trade.quantity = quantity;
        trade.executionPrice = executionPrice;
        trade.totalAmount = totalAmount;
        trade.requestedPrice = requestedPrice;
        trade.priceDeviationBps = priceDeviationBps;
        trade.snapshotAt = snapshotAt;
        trade.idempotencyKey = idempotencyKey;
        trade.executedAt = executedAt;
        return trade;
    }
}
