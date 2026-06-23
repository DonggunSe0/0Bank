package com.team10.backend.domain.exchange.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.exchange.calculator.ExchangeCalculator;
import com.team10.backend.domain.exchange.dto.res.ExchangeOrderRes;
import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.entity.ExchangeOrder;
import com.team10.backend.domain.exchange.entity.ExchangeQuote;
import com.team10.backend.domain.exchange.entity.FxWallet;
import com.team10.backend.domain.exchange.exception.ExchangeErrorCode;
import com.team10.backend.domain.exchange.repository.CurrencyRepository;
import com.team10.backend.domain.exchange.repository.ExchangeOrderRepository;
import com.team10.backend.domain.exchange.repository.ExchangeQuoteRepository;
import com.team10.backend.domain.exchange.repository.ExchangeRateRepository;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.CurrencyStatus;
import com.team10.backend.domain.exchange.type.ExchangeDirection;
import com.team10.backend.domain.exchange.type.ExchangeOrderStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.idempotency.annotation.Idempotent;
import com.team10.backend.global.idempotency.type.IdempotencyOperationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

    @Mock
    private ExchangeQuoteRepository exchangeQuoteRepository;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private ExchangeOrderRepository exchangeOrderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrencyRepository currencyRepository;

    @Mock
    private ExchangeCalculator exchangeCalculator;

    @Mock
    private ExchangeBusinessService exchangeBusinessService;

    @InjectMocks
    private ExchangeService exchangeService;

    @Test
    @DisplayName("비활성 통화로는 환전 견적을 생성할 수 없다")
    void createQuoteWithInactiveCurrency() {
        User user = createUser(1L);
        Currency krw = Currency.create(CurrencyCode.KRW, "한국 원", "대한민국", 0);
        Currency usd = Currency.create(CurrencyCode.USD, "미국 달러", "미국", 2);
        ReflectionTestUtils.setField(usd, "status", CurrencyStatus.INACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.KRW)).thenReturn(Optional.of(krw));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.USD)).thenReturn(Optional.of(usd));

        assertThatThrownBy(() -> exchangeService.createQuote(
                1L,
                CurrencyCode.KRW,
                CurrencyCode.USD,
                new BigDecimal("100000")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.CURRENCY_NOT_SUPPORTED);

        verify(exchangeRateRepository, never()).findByCurrencyCurrencyCode(CurrencyCode.USD);
        verify(exchangeQuoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("원화 금액에 소수점이 있으면 환전 견적을 생성할 수 없다")
    void createQuoteWithInvalidKrwAmountScale() {
        User user = createUser(1L);
        Currency krw = Currency.create(CurrencyCode.KRW, "한국 원", "대한민국", 0);
        Currency usd = Currency.create(CurrencyCode.USD, "미국 달러", "미국", 2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.KRW)).thenReturn(Optional.of(krw));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.USD)).thenReturn(Optional.of(usd));

        assertThatThrownBy(() -> exchangeService.createQuote(
                1L,
                CurrencyCode.KRW,
                CurrencyCode.USD,
                new BigDecimal("100000.1")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.INVALID_EXCHANGE_AMOUNT);

        verify(exchangeRateRepository, never()).findByCurrencyCurrencyCode(CurrencyCode.USD);
        verify(exchangeQuoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("출금 외화의 소수 자리수를 초과하면 환전 견적을 생성할 수 없다")
    void createQuoteWithInvalidForeignAmountScale() {
        User user = createUser(1L);
        Currency krw = Currency.create(CurrencyCode.KRW, "한국 원", "대한민국", 0);
        Currency usd = Currency.create(CurrencyCode.USD, "미국 달러", "미국", 2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.USD)).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.KRW)).thenReturn(Optional.of(krw));

        assertThatThrownBy(() -> exchangeService.createQuote(
                1L,
                CurrencyCode.USD,
                CurrencyCode.KRW,
                new BigDecimal("10.123")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.INVALID_EXCHANGE_AMOUNT);

        verify(exchangeRateRepository, never()).findByCurrencyCurrencyCode(CurrencyCode.USD);
        verify(exchangeQuoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("환전 주문은 멱등성 오케스트레이터에서 비즈니스 서비스로 위임한다")
    void createExchangeOrderDelegatesToBusinessService() {
        ExchangeOrderRes expected = new ExchangeOrderRes(
                40L,
                10L,
                ExchangeDirection.KRW_TO_FOREIGN,
                ExchangeOrderStatus.COMPLETED,
                20L,
                30L,
                new BigDecimal("100000"),
                new BigDecimal("72.2826"),
                new BigDecimal("1380.000000"),
                new BigDecimal("0.002500"),
                new BigDecimal("250.0000"),
                LocalDateTime.of(2026, 6, 21, 10, 0),
                LocalDateTime.of(2026, 6, 21, 10, 0, 1)
        );
        when(exchangeBusinessService.executeExchangeOrder(1L, 10L, 20L, 30L))
                .thenReturn(expected);

        ExchangeOrderRes response = exchangeService.createExchangeOrder(
                1L,
                "exchange-order-key",
                10L,
                20L,
                30L
        );

        assertThat(response).isSameAs(expected);
        verify(exchangeBusinessService).executeExchangeOrder(1L, 10L, 20L, 30L);
    }

    @Test
    @DisplayName("환전 주문 오케스트레이터에 멱등성 설정이 적용되어 있다")
    void createExchangeOrderHasIdempotentAnnotation() throws NoSuchMethodException {
        Method method = ExchangeService.class.getMethod(
                "createExchangeOrder",
                Long.class,
                String.class,
                Long.class,
                Long.class,
                Long.class
        );

        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        assertThat(idempotent).isNotNull();
        assertThat(idempotent.operationType()).isEqualTo(IdempotencyOperationType.EXCHANGE_ORDER);
        assertThat(idempotent.userId()).isEqualTo("#userId");
        assertThat(idempotent.key()).isEqualTo("#idempotencyKey");
        assertThat(idempotent.hashFields()).containsExactly("#exchangeQuoteId", "#krwAccountId", "#fxWalletId");
    }

    @Test
    @DisplayName("환전 주문 상세 조회는 인증 사용자의 주문을 조회해 응답으로 변환한다")
    void getExchangeOrder() {
        User user = createUser(1L);
        Currency krw = Currency.create(CurrencyCode.KRW, "한국 원", "대한민국", 0);
        Currency usd = Currency.create(CurrencyCode.USD, "미국 달러", "미국", 2);
        Account krwAccount = createAccount(20L, user);
        FxWallet fxWallet = createWallet(30L, user, usd);
        ExchangeQuote quote = createQuote(10L, user, krw, usd);
        ExchangeOrder order = createOrder(
                40L,
                user,
                quote,
                krwAccount,
                fxWallet,
                ExchangeDirection.KRW_TO_FOREIGN,
                LocalDateTime.of(2026, 6, 21, 10, 0, 1)
        );

        when(exchangeOrderRepository.findByIdAndUserId(40L, 1L)).thenReturn(Optional.of(order));

        ExchangeOrderRes response = exchangeService.getExchangeOrder(1L, 40L);

        assertThat(response.exchangeOrderId()).isEqualTo(40L);
        assertThat(response.exchangeQuoteId()).isEqualTo(10L);
        assertThat(response.direction()).isEqualTo(ExchangeDirection.KRW_TO_FOREIGN);
        assertThat(response.status()).isEqualTo(ExchangeOrderStatus.COMPLETED);
        assertThat(response.krwAccountId()).isEqualTo(20L);
        assertThat(response.fxWalletId()).isEqualTo(30L);
        assertThat(response.fromAmount()).isEqualByComparingTo("100000");
        assertThat(response.toAmount()).isEqualByComparingTo("72.2826");
        verify(exchangeOrderRepository).findByIdAndUserId(40L, 1L);
    }

    @Test
    @DisplayName("내 환전 주문 목록 조회는 인증 사용자의 주문 목록을 최신순으로 응답 변환한다")
    void getExchangeOrders() {
        User user = createUser(1L);
        Currency krw = Currency.create(CurrencyCode.KRW, "한국 원", "대한민국", 0);
        Currency usd = Currency.create(CurrencyCode.USD, "미국 달러", "미국", 2);
        Account krwAccount = createAccount(20L, user);
        FxWallet fxWallet = createWallet(30L, user, usd);
        ExchangeQuote firstQuote = createQuote(11L, user, usd, krw);
        ExchangeQuote secondQuote = createQuote(10L, user, krw, usd);
        ExchangeOrder firstOrder = createOrder(
                41L,
                user,
                firstQuote,
                krwAccount,
                fxWallet,
                ExchangeDirection.FOREIGN_TO_KRW,
                LocalDateTime.of(2026, 6, 21, 11, 0, 1)
        );
        ExchangeOrder secondOrder = createOrder(
                40L,
                user,
                secondQuote,
                krwAccount,
                fxWallet,
                ExchangeDirection.KRW_TO_FOREIGN,
                LocalDateTime.of(2026, 6, 21, 10, 0, 1) // firstOrder보다 더 이전에 주문
        );

        when(exchangeOrderRepository.findAllByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(firstOrder, secondOrder));

        List<ExchangeOrderRes> response = exchangeService.getExchangeOrders(1L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).exchangeOrderId()).isEqualTo(41L);
        assertThat(response.get(0).exchangeQuoteId()).isEqualTo(11L);
        assertThat(response.get(0).direction()).isEqualTo(ExchangeDirection.FOREIGN_TO_KRW);
        assertThat(response.get(1).exchangeOrderId()).isEqualTo(40L);
        assertThat(response.get(1).exchangeQuoteId()).isEqualTo(10L);
        assertThat(response.get(1).direction()).isEqualTo(ExchangeDirection.KRW_TO_FOREIGN);
        verify(exchangeOrderRepository).findAllByUserIdOrderByCreatedAtDesc(1L);
    }

    private User createUser(Long id) {
        User user = User.create(
                "user" + id + "@example.com",
                "password",
                "홍길동",
                "01012345678",
                LocalDate.of(1995, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Account createAccount(Long id, User user) {
        Account account = Account.create(user, "1000000000" + id, "입출금", AccountType.DEPOSIT);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private FxWallet createWallet(Long id, User user, Currency currency) {
        FxWallet wallet = FxWallet.create(user, currency);
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }

    private ExchangeQuote createQuote(Long id, User user, Currency fromCurrency, Currency toCurrency) {
        ExchangeQuote quote = ExchangeQuote.create(
                user,
                fromCurrency,
                toCurrency,
                new BigDecimal("100000"),
                new BigDecimal("1380.000000"),
                new BigDecimal("0.002500"),
                new BigDecimal("250.0000"),
                new BigDecimal("72.2826"),
                LocalDateTime.of(2026, 6, 21, 10, 5)
        );
        ReflectionTestUtils.setField(quote, "id", id);
        return quote;
    }

    private ExchangeOrder createOrder(
            Long id,
            User user,
            ExchangeQuote quote,
            Account krwAccount,
            FxWallet fxWallet,
            ExchangeDirection direction,
            LocalDateTime completedAt
    ) {
        ExchangeOrder order = ExchangeOrder.createCompleted(
                user,
                quote,
                krwAccount,
                fxWallet,
                direction,
                null,
                completedAt
        );
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
