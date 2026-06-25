package com.team10.backend.domain.exchange.entity;

import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.CurrencyStatus;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "currencies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Currency extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, unique = true, length = 3)
    private CurrencyCode currencyCode; // USD

    @Column(name = "currency_name", nullable = false, length = 50)
    private String currencyName; // "달러"

    @Column(name = "country_name", nullable = false, length = 50)
    private String countryName; // "미국"

    @Column(name = "decimal_places", nullable = false)
    private Integer decimalPlaces; // 2 (14.32 $)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CurrencyStatus status; // ACTIVE

    public static Currency create(
            CurrencyCode currencyCode,
            String currencyName,
            String countryName,
            Integer decimalPlaces
    ) {
        Currency currency = new Currency();
        currency.currencyCode = currencyCode;
        currency.currencyName = currencyName;
        currency.countryName = countryName;
        currency.decimalPlaces = decimalPlaces;
        currency.status = CurrencyStatus.ACTIVE;
        return currency;
    }
}
