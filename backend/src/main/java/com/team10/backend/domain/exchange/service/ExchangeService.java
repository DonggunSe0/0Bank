package com.team10.backend.domain.exchange.service;

import com.team10.backend.domain.exchange.calculator.ExchangeCalculator;
import com.team10.backend.domain.exchange.calculator.QuoteCalculation;
import com.team10.backend.domain.exchange.dto.res.ExchangeOrderRes;
import com.team10.backend.domain.exchange.dto.res.ExchangeQuoteRes;
import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.entity.ExchangeOrder;
import com.team10.backend.domain.exchange.entity.ExchangeQuote;
import com.team10.backend.domain.exchange.entity.ExchangeRate;
import com.team10.backend.domain.exchange.exception.ExchangeErrorCode;
import com.team10.backend.domain.exchange.repository.CurrencyRepository;
import com.team10.backend.domain.exchange.repository.ExchangeOrderRepository;
import com.team10.backend.domain.exchange.repository.ExchangeQuoteRepository;
import com.team10.backend.domain.exchange.repository.ExchangeRateRepository;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.CurrencyStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.idempotency.annotation.Idempotent;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExchangeService {

    private final ExchangeQuoteRepository exchangeQuoteRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeOrderRepository exchangeOrderRepository;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;
    private final ExchangeCalculator exchangeCalculator;
    private final ExchangeBusinessService exchangeBusinessService;

    @Transactional
    public ExchangeQuoteRes createQuote(
            Long userId,
            CurrencyCode fromCurrencyCode,
            CurrencyCode toCurrencyCode,
            BigDecimal fromAmount
    ) {
        // 사용자 조회 및 검증
        User user = findUser(userId);
        // 동일 통화 검증
        validateDifferentCurrencies(fromCurrencyCode, toCurrencyCode);
        validateKrwPair(fromCurrencyCode, toCurrencyCode);

        // 통화 조회
        Currency fromCurrency = findCurrency(fromCurrencyCode);
        Currency toCurrency = findCurrency(toCurrencyCode);
        validateActiveCurrency(fromCurrency);
        validateActiveCurrency(toCurrency);
        validateAmountScale(fromAmount, fromCurrency);

        CurrencyCode foreignCurrencyCode = resolveForeignCurrency(fromCurrencyCode, toCurrencyCode);

        // 최신 환율 조회
        ExchangeRate rate = exchangeRateRepository.findByCurrencyCurrencyCode(foreignCurrencyCode)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.EXCHANGE_RATE_NOT_FOUND));

        // 계산기 호출
        QuoteCalculation calculation = exchangeCalculator.calculate(
                fromCurrency.getCurrencyCode(),
                toCurrency.getCurrencyCode(),
                fromAmount,
                rate.getBasePrice(),
                rate.getCurrencyUnit(),
                fromCurrency.getDecimalPlaces(),
                toCurrency.getDecimalPlaces()
        );

        // ExchangeQuote 생성/저장
        ExchangeQuote quote = ExchangeQuote.create(
                user,
                fromCurrency,
                toCurrency,
                fromAmount,
                calculation.rate(),
                calculation.feeRate(),
                calculation.fee(),
                calculation.expectedToAmount(),
                LocalDateTime.now().plusMinutes(5)
        );

        return ExchangeQuoteRes.from(exchangeQuoteRepository.save(quote));
    }

    @Idempotent(
            operationType = IdempotencyOperationType.EXCHANGE_ORDER,
            userId = "#userId",
            key = "#idempotencyKey",
            hashFields = {"#exchangeQuoteId", "#krwAccountId", "#fxWalletId"}
    )
    public ExchangeOrderRes createExchangeOrder(
            Long userId,
            String idempotencyKey,
            Long exchangeQuoteId,
            Long krwAccountId,
            Long fxWalletId
    ) {
        return exchangeBusinessService.executeExchangeOrder(
                userId,
                exchangeQuoteId,
                krwAccountId,
                fxWalletId
        );
    }

    private void validateDifferentCurrencies(CurrencyCode fromCurrencyCode, CurrencyCode toCurrencyCode) {
        if (fromCurrencyCode == toCurrencyCode) {
            throw new BusinessException(ExchangeErrorCode.SAME_CURRENCY_EXCHANGE_NOT_ALLOWED);
        }
    }

    private void validateKrwPair(CurrencyCode fromCurrencyCode, CurrencyCode toCurrencyCode) {
        boolean fromIsKRW = fromCurrencyCode == CurrencyCode.KRW;
        boolean toIsKRW = toCurrencyCode == CurrencyCode.KRW;

        // 둘 다 KRW or !KRW => 유효하지 않은 환전 방향
        if (fromIsKRW == toIsKRW) {
            throw new BusinessException(ExchangeErrorCode.INVALID_EXCHANGE_DIRECTION);
        }
    }

    private Currency findCurrency(CurrencyCode currencyCode) {
        return currencyRepository.findByCurrencyCode(currencyCode)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.CURRENCY_NOT_FOUND));
    }

    private void validateActiveCurrency(Currency currency) {
        if (currency.getStatus() != CurrencyStatus.ACTIVE) {
            throw new BusinessException(ExchangeErrorCode.CURRENCY_NOT_SUPPORTED);
        }
    }

    private void validateAmountScale(BigDecimal amount, Currency currency) {
        // 출금 통화가 허용하는 소수 자리까지만 견적을 허용한다.
        // 예: KRW(decimalPlaces=0)는 1000.5 거부, USD(decimalPlaces=2)는 10.12 허용/10.123 거부.
        int actualScale = Math.max(amount.stripTrailingZeros().scale(), 0); // 금액 뒤쪽의 의미 없는 0을 제거 10.00 => 10 | 소수 자릿수 반환(음수 자릿수 보정)

        if (actualScale > currency.getDecimalPlaces()) {
            throw new BusinessException(ExchangeErrorCode.INVALID_EXCHANGE_AMOUNT);
        }
    }

    private CurrencyCode resolveForeignCurrency(CurrencyCode fromCurrencyCode, CurrencyCode toCurrencyCode) {
        boolean fromIsKRW = fromCurrencyCode == CurrencyCode.KRW;
        return fromIsKRW ? toCurrencyCode : fromCurrencyCode; // 외화만 반환
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.USER_NOT_FOUND));
    }

    // 환전 주문 전체 조회
    @Transactional(readOnly = true)
    public List<ExchangeOrderRes> getExchangeOrders(Long userId) {
        return exchangeOrderRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ExchangeOrderRes::from)
                .toList();
    }

    // 환전 주문 상세 조회
    @Transactional(readOnly = true)
    public ExchangeOrderRes getExchangeOrder(Long userId, Long exchangeOrderId) {
        ExchangeOrder order = exchangeOrderRepository.findByIdAndUserId(exchangeOrderId, userId)
                .orElseThrow(() -> new BusinessException(ExchangeErrorCode.EXCHANGE_ORDER_NOT_FOUND));

        return ExchangeOrderRes.from(order);
    }

}
