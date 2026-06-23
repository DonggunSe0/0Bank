package com.team10.backend.domain.exchange.entity;

import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "exchange_rates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code", referencedColumnName = "currency_code", nullable = false, unique = true)
    private Currency currency;

    @Column(name = "base_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal basePrice; // 현재 기준 외화 환율

    @Column(name = "currency_unit", nullable = false)
    private Integer currencyUnit; // 1원 기준 | 100원 기준

    @Column(name = "rate_at", nullable = false)
    private LocalDateTime rateAt;


    public static ExchangeRate create(
            Currency currency,
            BigDecimal basePrice,
            Integer currencyUnit,
            LocalDateTime rateAt
    ) {
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.currency = currency;
        exchangeRate.basePrice = basePrice;
        exchangeRate.currencyUnit = currencyUnit;
        exchangeRate.rateAt = rateAt;
        return exchangeRate;
    }

    public void update(BigDecimal basePrice, Integer currencyUnit, LocalDateTime rateAt) {
        this.basePrice = basePrice;
        this.currencyUnit = currencyUnit;
        this.rateAt = rateAt;
    }
}
