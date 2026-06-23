package com.team10.backend.domain.exchange.calculator;

import com.team10.backend.domain.exchange.exception.ExchangeErrorCode;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ExchangeCalculator {

    private static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.0025"); // 기본 수수료 0.25%
    private static final int RATE_SCALE = 10; // 환율 계산시 소수점 10자리까지 보관

    public QuoteCalculation calculate(
            CurrencyCode fromCurrencyCode,
            CurrencyCode toCurrencyCode,
            BigDecimal fromAmount,
            BigDecimal basePrice,
            Integer currencyUnit,
            Integer fromCurrencyDecimalPlaces,
            Integer toCurrencyDecimalPlaces
    ) {
        BigDecimal rate = calculateUnitRate(basePrice, currencyUnit);
        BigDecimal fee = calculateFee(fromAmount, DEFAULT_FEE_RATE, fromCurrencyDecimalPlaces);
        BigDecimal amountAfterFee = fromAmount.subtract(fee);

        BigDecimal expectedToAmount = calculateExpectedToAmount(
                fromCurrencyCode,
                toCurrencyCode,
                amountAfterFee,
                rate,
                toCurrencyDecimalPlaces
        );

        return new QuoteCalculation(
                rate,
                DEFAULT_FEE_RATE,
                fee,
                expectedToAmount
        );
    }

    // 환율 API에서 받은 값 -> 외화 1단위 기준 환율로 바꾸는 메서드(외화 1단위당 몇 KRW인가)
    // JPY: basePrice = 950, currencyUnit = 100 -> rate = 950 / 100 = 9.5
    private BigDecimal calculateUnitRate(BigDecimal basePrice, Integer currencyUnit) {
        return basePrice.divide(
                BigDecimal.valueOf(currencyUnit),
                RATE_SCALE,
                RoundingMode.HALF_UP // 반올림
        );
    }

    // 사용자가 환전하려는 금액 기준으로 수수료 금액을 계산하는 메서드
    private BigDecimal calculateFee(BigDecimal fromAmount, BigDecimal defaultFeeRate, Integer fromCurrencyDecimalPlaces) {
        return fromAmount.multiply(defaultFeeRate)
                .setScale(fromCurrencyDecimalPlaces, RoundingMode.DOWN); // 내림(절삭)
    }

    // 예상 수령 금액을 계산하는 메서드
    private BigDecimal calculateExpectedToAmount(
            CurrencyCode fromCurrencyCode,
            CurrencyCode toCurrencyCode,
            BigDecimal amountAfterFee,
            BigDecimal rate,
            Integer toCurrencyDecimalPlaces
    ) {
        // 원화 -> 외화 환전
        if (fromCurrencyCode == CurrencyCode.KRW) {
            // amountAfterFee = 99750 KRW, rate = 1380,
            // expectedToAmount = 99750 / 1380 = 72.28 USD (내림 -> 최대한 은행사에 유리하게)
            return amountAfterFee.divide(rate, toCurrencyDecimalPlaces, RoundingMode.DOWN);
        }

        // 외화 -> 원화 환전
        if (toCurrencyCode == CurrencyCode.KRW) {
            // amountAfterFee = 99.75 USD, rate = 1380, expectedToAmount = 99.75 * 1380 = 137655 KRW
            return amountAfterFee.multiply(rate)
                    .setScale(toCurrencyDecimalPlaces, RoundingMode.DOWN); // 소수점 처리
        }

        throw new BusinessException(ExchangeErrorCode.INVALID_EXCHANGE_DIRECTION);
    }

}
