package com.team10.backend.domain.exchange.service;

import com.team10.backend.domain.exchange.client.UpbitExchangeRateClient;
import com.team10.backend.domain.exchange.dto.res.CurrencyRes;
import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.exception.ExchangeErrorCode;
import com.team10.backend.domain.exchange.repository.CurrencyRepository;
import com.team10.backend.domain.exchange.repository.ExchangeRateCacheRepository;
import com.team10.backend.domain.exchange.repository.ExchangeRateRepository;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.CurrencyStatus;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private UpbitExchangeRateClient upbitExchangeRateClient;

    @Mock
    private CurrencyRepository currencyRepository;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private ExchangeRateCacheRepository exchangeRateCacheRepository;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    @Test
    @DisplayName("지원 통화 목록은 ACTIVE 외화만 반환한다")
    void getCurrenciesReturnsOnlyActiveForeignCurrencies() {
        Currency usd = Currency.create(CurrencyCode.USD, "미국 달러", "미국", 2);
        when(currencyRepository.findAllByCurrencyCodeInAndStatus(anyCollection(), org.mockito.ArgumentMatchers.eq(CurrencyStatus.ACTIVE)))
                .thenReturn(List.of(usd));

        List<CurrencyRes> responses = exchangeRateService.getCurrencies();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).currencyCode()).isEqualTo(CurrencyCode.USD);
        assertThat(responses.get(0).status()).isEqualTo(CurrencyStatus.ACTIVE);
    }

    @Test
    @DisplayName("비활성 통화의 단건 환율은 캐시 조회 전에 거부한다")
    void getLatestRateWithInactiveCurrency() {
        Currency usd = Currency.create(CurrencyCode.USD, "미국 달러", "미국", 2);
        ReflectionTestUtils.setField(usd, "status", CurrencyStatus.INACTIVE);
        when(currencyRepository.findByCurrencyCode(CurrencyCode.USD)).thenReturn(Optional.of(usd));

        assertThatThrownBy(() -> exchangeRateService.getLatestRate(CurrencyCode.USD))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.CURRENCY_NOT_SUPPORTED);

        verify(exchangeRateCacheRepository, never()).findByCurrency(CurrencyCode.USD);
        verify(exchangeRateRepository, never()).findByCurrencyCurrencyCode(CurrencyCode.USD);
    }
}
