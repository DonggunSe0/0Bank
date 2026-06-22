package com.team10.backend.domain.exchange.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.exchange.dto.res.ExchangeOrderRes;
import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.entity.ExchangeOrder;
import com.team10.backend.domain.exchange.entity.ExchangeQuote;
import com.team10.backend.domain.exchange.entity.FxWallet;
import com.team10.backend.domain.exchange.exception.ExchangeErrorCode;
import com.team10.backend.domain.exchange.repository.ExchangeOrderRepository;
import com.team10.backend.domain.exchange.repository.ExchangeQuoteRepository;
import com.team10.backend.domain.exchange.repository.FxWalletRepository;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.ExchangeDirection;
import com.team10.backend.domain.exchange.type.ExchangeOrderStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeBusinessServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private FxWalletRepository fxWalletRepository;

    @Mock
    private ExchangeOrderRepository exchangeOrderRepository;

    @Mock
    private ExchangeQuoteRepository exchangeQuoteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ExchangeBusinessService exchangeBusinessService;

    private User user;
    private User otherUser;
    private Currency krw;
    private Currency usd;
    private Currency jpy;

    @BeforeEach
    void setUp() {
        user = createUser(1L);
        otherUser = createUser(2L);
        krw = Currency.create(CurrencyCode.KRW, "한국 원", "대한민국", 0);
        usd = Currency.create(CurrencyCode.USD, "미국 달러", "미국", 2);
        jpy = Currency.create(CurrencyCode.JPY, "일본 엔", "일본", 0);
    }

    @Test
    @DisplayName("원화에서 외화로 환전 주문을 실행한다")
    void executeExchangeOrderKrwToForeign() {
        ExchangeQuote quote = createQuote(
                10L,
                user,
                krw,
                usd,
                new BigDecimal("100000"),
                new BigDecimal("1380.000000"),
                new BigDecimal("250.0000"),
                new BigDecimal("72.2826"),
                LocalDateTime.now().plusMinutes(5)
        );
        Account krwAccount = createAccount(20L, user, 200000L);
        FxWallet fxWallet = createWallet(30L, user, usd, BigDecimal.ZERO);
        mockSuccessFlow(quote, krwAccount, fxWallet);

        ExchangeOrderRes response = exchangeBusinessService.executeExchangeOrder(
                1L,
                10L,
                20L,
                30L
        );

        assertThat(response.exchangeOrderId()).isEqualTo(40L);
        assertThat(response.exchangeQuoteId()).isEqualTo(10L);
        assertThat(response.direction()).isEqualTo(ExchangeDirection.KRW_TO_FOREIGN);
        assertThat(response.status()).isEqualTo(ExchangeOrderStatus.COMPLETED);
        assertThat(krwAccount.getBalance()).isEqualTo(100000L);
        assertThat(fxWallet.getBalance()).isEqualByComparingTo("72.2826");
        verify(exchangeOrderRepository).saveAndFlush(any(ExchangeOrder.class));
    }

    @Test
    @DisplayName("외화에서 원화로 환전 주문을 실행한다")
    void executeExchangeOrderForeignToKrw() {
        ExchangeQuote quote = createQuote(
                10L,
                user,
                usd,
                krw,
                new BigDecimal("10.0000"),
                new BigDecimal("1375.000000"),
                new BigDecimal("34.0000"),
                new BigDecimal("13716.0000"),
                LocalDateTime.now().plusMinutes(5)
        );
        Account krwAccount = createAccount(20L, user, 100000L);
        FxWallet fxWallet = createWallet(30L, user, usd, new BigDecimal("20.0000"));
        mockSuccessFlow(quote, krwAccount, fxWallet);

        ExchangeOrderRes response = exchangeBusinessService.executeExchangeOrder(
                1L,
                10L,
                20L,
                30L
        );

        assertThat(response.direction()).isEqualTo(ExchangeDirection.FOREIGN_TO_KRW);
        assertThat(response.status()).isEqualTo(ExchangeOrderStatus.COMPLETED);
        assertThat(krwAccount.getBalance()).isEqualTo(113716L);
        assertThat(fxWallet.getBalance()).isEqualByComparingTo("10.0000");
        verify(exchangeOrderRepository).saveAndFlush(any(ExchangeOrder.class));
    }

    @Test
    @DisplayName("타인의 견적은 환전 주문에 사용할 수 없다")
    void executeExchangeOrderWithOtherUserQuote() {
        ExchangeQuote quote = createQuote(
                10L,
                otherUser,
                krw,
                usd,
                new BigDecimal("100000"),
                new BigDecimal("1380.000000"),
                new BigDecimal("250.0000"),
                new BigDecimal("72.2826"),
                LocalDateTime.now().plusMinutes(5)
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(exchangeQuoteRepository.findById(10L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> exchangeBusinessService.executeExchangeOrder(
                1L,
                10L,
                20L,
                30L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.EXCHANGE_QUOTE_ACCESS_DENIED);

        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(exchangeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 사용된 견적은 환전 주문에 다시 사용할 수 없다")
    void executeExchangeOrderWithUsedQuote() {
        ExchangeQuote quote = createQuote(
                10L,
                user,
                krw,
                usd,
                new BigDecimal("100000"),
                new BigDecimal("1380.000000"),
                new BigDecimal("250.0000"),
                new BigDecimal("72.2826"),
                LocalDateTime.now().plusMinutes(5)
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(exchangeQuoteRepository.findById(10L)).thenReturn(Optional.of(quote));
        when(exchangeOrderRepository.existsByExchangeQuote_Id(10L)).thenReturn(true);

        assertThatThrownBy(() -> exchangeBusinessService.executeExchangeOrder(
                1L,
                10L,
                20L,
                30L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.EXCHANGE_QUOTE_ALREADY_USED);

        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(exchangeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("만료된 견적은 환전 주문에 사용할 수 없다")
    void executeExchangeOrderWithExpiredQuote() {
        ExchangeQuote quote = createQuote(
                10L,
                user,
                krw,
                usd,
                new BigDecimal("100000"),
                new BigDecimal("1380.000000"),
                new BigDecimal("250.0000"),
                new BigDecimal("72.2826"),
                LocalDateTime.now().minusSeconds(1)
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(exchangeQuoteRepository.findById(10L)).thenReturn(Optional.of(quote));
        when(exchangeOrderRepository.existsByExchangeQuote_Id(10L)).thenReturn(false);

        assertThatThrownBy(() -> exchangeBusinessService.executeExchangeOrder(
                1L,
                10L,
                20L,
                30L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.EXCHANGE_QUOTE_EXPIRED);

        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(exchangeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("외화 지갑 통화가 견적의 외화와 다르면 환전 주문에 실패한다")
    void executeExchangeOrderWithWalletCurrencyMismatch() {
        ExchangeQuote quote = createQuote(
                10L,
                user,
                krw,
                usd,
                new BigDecimal("100000"),
                new BigDecimal("1380.000000"),
                new BigDecimal("250.0000"),
                new BigDecimal("72.2826"),
                LocalDateTime.now().plusMinutes(5)
        );
        Account krwAccount = createAccount(20L, user, 200000L);
        FxWallet fxWallet = createWallet(30L, user, jpy, BigDecimal.ZERO);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(exchangeQuoteRepository.findById(10L)).thenReturn(Optional.of(quote));
        when(exchangeOrderRepository.existsByExchangeQuote_Id(10L)).thenReturn(false);
        when(accountRepository.findByIdAndUserIdForUpdate(20L, 1L)).thenReturn(Optional.of(krwAccount));
        when(fxWalletRepository.findByIdAndUserIdForUpdate(30L, 1L)).thenReturn(Optional.of(fxWallet));

        assertThatThrownBy(() -> exchangeBusinessService.executeExchangeOrder(
                1L,
                10L,
                20L,
                30L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.FX_WALLET_CURRENCY_MISMATCH);

        assertThat(krwAccount.getBalance()).isEqualTo(200000L);
        assertThat(fxWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(exchangeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("원화 금액에 소수점이 있으면 환전 주문에 실패한다")
    void executeExchangeOrderWithInvalidKrwAmount() {
        ExchangeQuote quote = createQuote(
                10L,
                user,
                krw,
                usd,
                new BigDecimal("100000.1000"),
                new BigDecimal("1380.000000"),
                new BigDecimal("250.0000"),
                new BigDecimal("72.2826"),
                LocalDateTime.now().plusMinutes(5)
        );
        Account krwAccount = createAccount(20L, user, 200000L);
        FxWallet fxWallet = createWallet(30L, user, usd, BigDecimal.ZERO);
        mockSuccessFlowWithoutSave(quote, krwAccount, fxWallet);

        assertThatThrownBy(() -> exchangeBusinessService.executeExchangeOrder(
                1L,
                10L,
                20L,
                30L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.INVALID_EXCHANGE_AMOUNT);

        assertThat(krwAccount.getBalance()).isEqualTo(200000L);
        assertThat(fxWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(exchangeOrderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("주문 저장 중 견적 유니크 제약 위반이 발생하면 이미 사용된 견적으로 처리한다")
    void executeExchangeOrderWithDuplicateQuoteUniqueViolation() {
        ExchangeQuote quote = createQuote(
                10L,
                user,
                krw,
                usd,
                new BigDecimal("100000"),
                new BigDecimal("1380.000000"),
                new BigDecimal("250.0000"),
                new BigDecimal("72.2826"),
                LocalDateTime.now().plusMinutes(5)
        );
        Account krwAccount = createAccount(20L, user, 200000L);
        FxWallet fxWallet = createWallet(30L, user, usd, BigDecimal.ZERO);
        mockSuccessFlowWithoutSave(quote, krwAccount, fxWallet);
        when(exchangeOrderRepository.saveAndFlush(any(ExchangeOrder.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate exchange quote"));

        assertThatThrownBy(() -> exchangeBusinessService.executeExchangeOrder(
                1L,
                10L,
                20L,
                30L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.EXCHANGE_QUOTE_ALREADY_USED);

        verify(exchangeOrderRepository).saveAndFlush(any(ExchangeOrder.class));
    }

    private void mockSuccessFlow(ExchangeQuote quote, Account krwAccount, FxWallet fxWallet) {
        mockSuccessFlowWithoutSave(quote, krwAccount, fxWallet);
        when(exchangeOrderRepository.saveAndFlush(any(ExchangeOrder.class))).thenAnswer(invocation -> {
            ExchangeOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 40L);
            return order;
        });
    }

    private void mockSuccessFlowWithoutSave(ExchangeQuote quote, Account krwAccount, FxWallet fxWallet) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(exchangeQuoteRepository.findById(10L)).thenReturn(Optional.of(quote));
        when(exchangeOrderRepository.existsByExchangeQuote_Id(10L)).thenReturn(false);
        when(accountRepository.findByIdAndUserIdForUpdate(20L, 1L)).thenReturn(Optional.of(krwAccount));
        when(fxWalletRepository.findByIdAndUserIdForUpdate(30L, 1L)).thenReturn(Optional.of(fxWallet));
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

    private Account createAccount(Long id, User user, Long balance) {
        Account account = Account.create(user, "1000000000" + id, "입출금", AccountType.DEPOSIT);
        account.deposit(balance);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private FxWallet createWallet(Long id, User user, Currency currency, BigDecimal balance) {
        FxWallet wallet = FxWallet.create(user, currency);
        wallet.deposit(balance);
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }

    private ExchangeQuote createQuote(
            Long id,
            User user,
            Currency fromCurrency,
            Currency toCurrency,
            BigDecimal fromAmount,
            BigDecimal rate,
            BigDecimal fee,
            BigDecimal expectedToAmount,
            LocalDateTime expiredAt
    ) {
        ExchangeQuote quote = ExchangeQuote.create(
                user,
                fromCurrency,
                toCurrency,
                fromAmount,
                rate,
                new BigDecimal("0.002500"),
                fee,
                expectedToAmount,
                expiredAt
        );
        ReflectionTestUtils.setField(quote, "id", id);
        return quote;
    }
}
