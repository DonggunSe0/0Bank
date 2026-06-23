package com.team10.backend.domain.exchange.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.exchange.dto.req.ExchangeOrderCreateReq;
import com.team10.backend.domain.exchange.dto.req.ExchangeQuoteCreateReq;
import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.entity.ExchangeOrder;
import com.team10.backend.domain.exchange.entity.ExchangeRate;
import com.team10.backend.domain.exchange.entity.FxWallet;
import com.team10.backend.domain.exchange.entity.FxWalletLedger;
import com.team10.backend.domain.exchange.repository.CurrencyRepository;
import com.team10.backend.domain.exchange.repository.ExchangeOrderRepository;
import com.team10.backend.domain.exchange.repository.ExchangeQuoteRepository;
import com.team10.backend.domain.exchange.repository.ExchangeRateRepository;
import com.team10.backend.domain.exchange.repository.FxWalletLedgerRepository;
import com.team10.backend.domain.exchange.repository.FxWalletRepository;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.ExchangeDirection;
import com.team10.backend.domain.exchange.type.ExchangeOrderStatus;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.repository.IdempotencyRepository;
import com.team10.backend.global.idempotency.type.IdempotencyStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExchangeFlowE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private ExchangeQuoteRepository exchangeQuoteRepository;

    @Autowired
    private ExchangeOrderRepository exchangeOrderRepository;

    @Autowired
    private FxWalletRepository fxWalletRepository;

    @Autowired
    private FxWalletLedgerRepository fxWalletLedgerRepository;

    @Autowired
    private TransactionHistoryRepository transactionHistoryRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("원화에서 외화 환전 E2E - 견적 생성, 주문 실행, 계좌/지갑/원장/멱등성 상태를 검증한다")
    void exchangeKrwToForeign_success_persistsConsistentState() throws Exception {
        // given. 환율 기준 데이터, 사용자, 원화 계좌, USD 지갑을 준비한다.
        User user = saveUser("exchange-buy@example.com", "환전자");
        Currency krw = saveCurrency(CurrencyCode.KRW, "원", "대한민국", 0);
        Currency usd = saveCurrency(CurrencyCode.USD, "미국 달러", "미국", 2);
        saveExchangeRate(usd, "1380.000000", 1);
        Account krwAccount = saveAccount(user, "200300400001", 200_000L);
        FxWallet fxWallet = saveFxWallet(user, usd, BigDecimal.ZERO);
        String idempotencyKey = "exchange-flow-buy-key";

        ExchangeQuoteCreateReq quoteRequest = new ExchangeQuoteCreateReq(
                CurrencyCode.KRW,
                CurrencyCode.USD,
                new BigDecimal("100000")
        );

        // when 1. KRW -> USD 환전 견적을 생성한다.
        String quoteResponse = mockMvc.perform(post("/api/v1/exchanges/currencies/quotes")
                        .with(authentication(authenticatedUser(user.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromCurrencyCode").value("KRW"))
                .andExpect(jsonPath("$.toCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.fromAmount").value(100000))
                .andExpect(jsonPath("$.rate").value(1380.000000))
                .andExpect(jsonPath("$.fee").value(250))
                .andExpect(jsonPath("$.expectedToAmount").value(72.28))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long quoteId = readLong(quoteResponse, "exchangeQuoteId");
        ExchangeOrderCreateReq orderRequest = new ExchangeOrderCreateReq(
                quoteId,
                krwAccount.getId(),
                fxWallet.getId()
        );

        // when 2. 생성한 견적으로 환전 주문을 실행한다.
        mockMvc.perform(post("/api/v1/exchanges/currencies/orders")
                        .with(authentication(authenticatedUser(user.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exchangeQuoteId").value(quoteId))
                .andExpect(jsonPath("$.direction").value("KRW_TO_FOREIGN"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.krwAccountId").value(krwAccount.getId()))
                .andExpect(jsonPath("$.fxWalletId").value(fxWallet.getId()))
                .andExpect(jsonPath("$.fromAmount").value(100000))
                .andExpect(jsonPath("$.toAmount").value(72.28))
                .andExpect(jsonPath("$.fee").value(250));

        entityManager.clear();

        // then 1. 실제 잔액은 원화 차감과 외화 입금이 함께 반영되어야 한다.
        Account savedKrwAccount = accountRepository.findById(krwAccount.getId()).orElseThrow();
        FxWallet savedFxWallet = fxWalletRepository.findById(fxWallet.getId()).orElseThrow();

        assertEquals(100_000L, savedKrwAccount.getBalance());
        assertThat(savedFxWallet.getBalance()).isEqualByComparingTo("72.28");

        // then 2. 견적과 완료 주문은 각각 1건씩 저장되어야 한다.
        assertEquals(1, exchangeQuoteRepository.count());
        List<ExchangeOrder> orders = exchangeOrderRepository.findAll();
        assertEquals(1, orders.size());

        ExchangeOrder order = orders.getFirst();
        assertEquals(ExchangeDirection.KRW_TO_FOREIGN, order.getDirection());
        assertEquals(ExchangeOrderStatus.COMPLETED, order.getStatus());
        assertEquals(quoteId, order.getExchangeQuote().getId());
        assertThat(order.getFromAmount()).isEqualByComparingTo("100000");
        assertThat(order.getToAmount()).isEqualByComparingTo("72.28");
        assertThat(order.getFee()).isEqualByComparingTo("250");
        assertNotNull(order.getCompletedAt());

        // then 3. 원화 계좌 거래내역과 외화 지갑 원장이 각각 1건씩 저장되어야 한다.
        List<TransactionHistory> histories = transactionHistoryRepository.findAll();
        assertEquals(1, histories.size());

        TransactionHistory krwHistory = histories.getFirst();
        assertEquals(savedKrwAccount.getId(), krwHistory.getAccount().getId());
        assertEquals(TransactionType.EXCHANGE, krwHistory.getType());
        assertEquals(TransactionDirection.OUT, krwHistory.getDirection());
        assertEquals(100_000L, krwHistory.getAmount());
        assertEquals(200_000L, krwHistory.getBalanceBefore());
        assertEquals(100_000L, krwHistory.getBalanceAfter());
        assertEquals("환전", krwHistory.getMemo());
        assertNotNull(krwHistory.getTransactedAt());

        List<FxWalletLedger> ledgers = fxWalletLedgerRepository.findAll();
        assertEquals(1, ledgers.size());

        FxWalletLedger ledger = ledgers.getFirst();
        assertEquals(savedFxWallet.getId(), ledger.getFxWallet().getId());
        assertEquals(order.getId(), ledger.getExchangeOrder().getId());
        assertEquals(TransactionDirection.IN, ledger.getDirection());
        assertThat(ledger.getAmount()).isEqualByComparingTo("72.28");
        assertThat(ledger.getBalanceBefore()).isEqualByComparingTo("0");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("72.28");
        assertNotNull(ledger.getTransactedAt());

        // then 4. 멱등성 레코드는 SUCCESS로 완료되고 replay용 응답을 보관해야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(user.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.SUCCESS, idempotency.getStatus());
        assertNotNull(idempotency.getResponseBody());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("외화에서 원화 환전 E2E - 견적 생성, 주문 실행, 지갑/계좌/원장/멱등성 상태를 검증한다")
    void exchangeForeignToKrw_success_persistsConsistentState() throws Exception {
        // given. 환율 기준 데이터, 사용자, 원화 계좌, 잔액이 있는 USD 지갑을 준비한다.
        User user = saveUser("exchange-sell@example.com", "환전자");
        Currency krw = saveCurrency(CurrencyCode.KRW, "원", "대한민국", 0);
        Currency usd = saveCurrency(CurrencyCode.USD, "미국 달러", "미국", 2);
        saveExchangeRate(usd, "1380.000000", 1);
        Account krwAccount = saveAccount(user, "200300400002", 100_000L);
        FxWallet fxWallet = saveFxWallet(user, usd, new BigDecimal("20.00"));
        String idempotencyKey = "exchange-flow-sell-key";

        ExchangeQuoteCreateReq quoteRequest = new ExchangeQuoteCreateReq(
                CurrencyCode.USD,
                CurrencyCode.KRW,
                new BigDecimal("10.00")
        );

        // when 1. USD -> KRW 환전 견적을 생성한다.
        String quoteResponse = mockMvc.perform(post("/api/v1/exchanges/currencies/quotes")
                        .with(authentication(authenticatedUser(user.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.toCurrencyCode").value("KRW"))
                .andExpect(jsonPath("$.fromAmount").value(10.00))
                .andExpect(jsonPath("$.rate").value(1380.000000))
                .andExpect(jsonPath("$.fee").value(0.02))
                .andExpect(jsonPath("$.expectedToAmount").value(13772))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long quoteId = readLong(quoteResponse, "exchangeQuoteId");
        ExchangeOrderCreateReq orderRequest = new ExchangeOrderCreateReq(
                quoteId,
                krwAccount.getId(),
                fxWallet.getId()
        );

        // when 2. 생성한 견적으로 환전 주문을 실행한다.
        mockMvc.perform(post("/api/v1/exchanges/currencies/orders")
                        .with(authentication(authenticatedUser(user.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exchangeQuoteId").value(quoteId))
                .andExpect(jsonPath("$.direction").value("FOREIGN_TO_KRW"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.krwAccountId").value(krwAccount.getId()))
                .andExpect(jsonPath("$.fxWalletId").value(fxWallet.getId()))
                .andExpect(jsonPath("$.fromAmount").value(10.00))
                .andExpect(jsonPath("$.toAmount").value(13772))
                .andExpect(jsonPath("$.fee").value(0.02));

        entityManager.clear();

        // then 1. 실제 잔액은 외화 차감과 원화 입금이 함께 반영되어야 한다.
        Account savedKrwAccount = accountRepository.findById(krwAccount.getId()).orElseThrow();
        FxWallet savedFxWallet = fxWalletRepository.findById(fxWallet.getId()).orElseThrow();

        assertEquals(113_772L, savedKrwAccount.getBalance());
        assertThat(savedFxWallet.getBalance()).isEqualByComparingTo("10.00");

        // then 2. 견적과 완료 주문은 각각 1건씩 저장되어야 한다.
        assertEquals(1, exchangeQuoteRepository.count());
        List<ExchangeOrder> orders = exchangeOrderRepository.findAll();
        assertEquals(1, orders.size());

        ExchangeOrder order = orders.getFirst();
        assertEquals(ExchangeDirection.FOREIGN_TO_KRW, order.getDirection());
        assertEquals(ExchangeOrderStatus.COMPLETED, order.getStatus());
        assertEquals(quoteId, order.getExchangeQuote().getId());
        assertThat(order.getFromAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getToAmount()).isEqualByComparingTo("13772");
        assertThat(order.getFee()).isEqualByComparingTo("0.02");
        assertNotNull(order.getCompletedAt());

        // then 3. 원화 계좌 거래내역과 외화 지갑 원장이 각각 1건씩 저장되어야 한다.
        List<TransactionHistory> histories = transactionHistoryRepository.findAll();
        assertEquals(1, histories.size());

        TransactionHistory krwHistory = histories.getFirst();
        assertEquals(savedKrwAccount.getId(), krwHistory.getAccount().getId());
        assertEquals(TransactionType.EXCHANGE, krwHistory.getType());
        assertEquals(TransactionDirection.IN, krwHistory.getDirection());
        assertEquals(13_772L, krwHistory.getAmount());
        assertEquals(100_000L, krwHistory.getBalanceBefore());
        assertEquals(113_772L, krwHistory.getBalanceAfter());
        assertEquals("환전", krwHistory.getMemo());
        assertNotNull(krwHistory.getTransactedAt());

        List<FxWalletLedger> ledgers = fxWalletLedgerRepository.findAll();
        assertEquals(1, ledgers.size());

        FxWalletLedger ledger = ledgers.getFirst();
        assertEquals(savedFxWallet.getId(), ledger.getFxWallet().getId());
        assertEquals(order.getId(), ledger.getExchangeOrder().getId());
        assertEquals(TransactionDirection.OUT, ledger.getDirection());
        assertThat(ledger.getAmount()).isEqualByComparingTo("10.00");
        assertThat(ledger.getBalanceBefore()).isEqualByComparingTo("20.00");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("10.00");
        assertNotNull(ledger.getTransactedAt());

        // then 4. 멱등성 레코드는 SUCCESS로 완료되고 replay용 응답을 보관해야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(user.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.SUCCESS, idempotency.getStatus());
        assertNotNull(idempotency.getResponseBody());
        assertNotNull(idempotency.getCompletedAt());
    }

    private UsernamePasswordAuthenticationToken authenticatedUser(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long readLong(String json, String fieldName) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        return root.get(fieldName).asLong();
    }

    private User saveUser(String email, String name) {
        return transactionTemplate.execute(status -> {
            User user = newUser();
            ReflectionTestUtils.setField(user, "email", email);
            ReflectionTestUtils.setField(user, "password", "password");
            ReflectionTestUtils.setField(user, "name", name);
            ReflectionTestUtils.setField(user, "phoneNumber", "01012345678");
            ReflectionTestUtils.setField(user, "birthDate", LocalDate.of(1990, 1, 1));
            ReflectionTestUtils.setField(user, "identityVerified", true);
            entityManager.persist(user);
            return user;
        });
    }

    private User newUser() {
        try {
            Constructor<User> constructor = User.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("User test fixture creation failed", e);
        }
    }

    private Currency saveCurrency(
            CurrencyCode currencyCode,
            String currencyName,
            String countryName,
            Integer decimalPlaces
    ) {
        return transactionTemplate.execute(status ->
                currencyRepository.save(Currency.create(
                        currencyCode,
                        currencyName,
                        countryName,
                        decimalPlaces
                ))
        );
    }

    private ExchangeRate saveExchangeRate(Currency currency, String basePrice, Integer currencyUnit) {
        return transactionTemplate.execute(status ->
                exchangeRateRepository.save(ExchangeRate.create(
                        currency,
                        new BigDecimal(basePrice),
                        currencyUnit,
                        LocalDateTime.of(2026, 6, 23, 10, 0)
                ))
        );
    }

    private Account saveAccount(User user, String accountNumber, Long balance) {
        Account account = Account.create(user, accountNumber, "테스트 계좌", AccountType.DEPOSIT);
        account.deposit(balance);
        return transactionTemplate.execute(status -> accountRepository.save(account));
    }

    private FxWallet saveFxWallet(User user, Currency currency, BigDecimal balance) {
        FxWallet fxWallet = FxWallet.create(user, currency);
        fxWallet.deposit(balance);
        return transactionTemplate.execute(status -> fxWalletRepository.save(fxWallet));
    }

    private void cleanDatabase() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM fx_wallet_ledgers").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM exchange_orders").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM exchange_quotes").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM exchange_rates").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM fx_wallets").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM transaction_histories").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM idempotency_keys").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM accounts").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM currencies").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
            entityManager.clear();
        });
    }
}
