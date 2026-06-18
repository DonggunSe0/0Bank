package com.team10.backend.domain.exchange.dto.res;

import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.CurrencyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지원 통화 응답")
public record CurrencyRes(
        @Schema(description = "통화 ID", example = "1")
        Long currencyId,

        @Schema(description = "통화 코드", example = "USD")
        CurrencyCode currencyCode,

        @Schema(description = "통화명", example = "미국 달러")
        String currencyName,

        @Schema(description = "국가명", example = "미국")
        String countryName,

        @Schema(description = "소수점 자리수", example = "2")
        Integer decimalPlaces,

        @Schema(description = "통화 상태", example = "ACTIVE")
        CurrencyStatus status
) {

    public static CurrencyRes from(Currency currency) {
        return new CurrencyRes(
                currency.getId(),
                currency.getCurrencyCode(),
                currency.getCurrencyName(),
                currency.getCountryName(),
                currency.getDecimalPlaces(),
                currency.getStatus()
        );
    }
}
