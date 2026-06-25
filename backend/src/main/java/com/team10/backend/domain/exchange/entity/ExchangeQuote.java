package com.team10.backend.domain.exchange.entity;

import com.team10.backend.domain.exchange.type.CurrencyCode;
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
@Table(name = "exchange_quotes") // 환전 견적 엔터티, 실제 환전 주문이 체결되기 전에, 사용자에게 보여준 환율/수수료/예상 수령액을 고정해두는 객체
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeQuote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 견적을 요청한 사용자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_currency_code", referencedColumnName = "currency_code", nullable = false)
    private Currency fromCurrency; // 환전 출발 통화

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_currency_code", referencedColumnName = "currency_code", nullable = false)
    private Currency toCurrency;   // 환전 도착 통화

    @Column(name = "from_amount", nullable = false, precision = 19, scale = 4) // 전체 숫자 자릿수 최대 19자리, 소수점아래 4자리까지
    private BigDecimal fromAmount; // 환전하려는 기준 금액

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal rate; // 적용 환율

    @Column(nullable = false, precision = 9, scale = 6) // 999.999999
    private BigDecimal feeRate; // 수수료율 (0.25%   -> 0.002500)

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee; // 최종 환전 수수료 금액

    @Column(name = "expected_to_amount", nullable = false, precision = 19, scale = 4) // 전체 숫자 자릿수 최대 19자리, 소수점아래 4자리까지
    private BigDecimal expectedToAmount; // 예상 수령 금액

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;    // 견적 만료 시각

    public static ExchangeQuote create(
            User user,
            Currency fromCurrency,
            Currency toCurrency,
            BigDecimal fromAmount,
            BigDecimal rate,
            BigDecimal feeRate,
            BigDecimal fee,
            BigDecimal expectedToAmount,
            LocalDateTime expiredAt
    ) {
        ExchangeQuote quote = new ExchangeQuote();
        quote.user = user;
        quote.fromCurrency = fromCurrency;
        quote.toCurrency = toCurrency;
        quote.fromAmount = fromAmount;
        quote.rate = rate;
        quote.feeRate = feeRate;
        quote.fee = fee;
        quote.expectedToAmount = expectedToAmount;
        quote.expiredAt = expiredAt;
        return quote;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiredAt.isAfter(now);
    }

    // 외화 반환 메서드
    public Currency getFxCurrency() {
        return fromCurrency.getCurrencyCode() != CurrencyCode.KRW
                ? fromCurrency : toCurrency;
    }

}
