package com.team10.backend.domain.exchange.service;

import com.team10.backend.domain.exchange.client.UpbitExchangeRateClient;
import com.team10.backend.domain.exchange.client.UpbitExchangeRateRes;
import com.team10.backend.domain.exchange.dto.res.ExchangeRateRes;
import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.entity.ExchangeRate;
import com.team10.backend.domain.exchange.repository.CurrencyRepository;
import com.team10.backend.domain.exchange.repository.ExchangeRateCacheRepository;
import com.team10.backend.domain.exchange.repository.ExchangeRateRepository;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExchangeRateServiceIntegrationTest {

    private static final String CACHE_KEY = "exchange-rate:latest";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private ExchangeRateCacheRepository exchangeRateCacheRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private UpbitExchangeRateClient upbitExchangeRateClient;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(CACHE_KEY);
    }

    @Test
    @DisplayName("Upbit 환율 응답을 Currency와 ExchangeRate로 적재한다")
    void syncCurrentRatesCreatesCurrenciesAndExchangeRates() {
        given(upbitExchangeRateClient.fetch(anyList()))
                .willReturn(List.of(
                        rate("USD", "달러", "미국", "1519.50", 1, LocalTime.of(12, 14, 12)),
                        rate("JPY", "엔", "일본", "948.35", 100, LocalTime.of(12, 14, 12)),
                        rate("CNY", "위안", "중국", "224.67", 1, LocalTime.of(12, 14, 12)),
                        rate("XXX", "미지원", "미지원", "1.00", 1, LocalTime.of(12, 14, 12))
                ));

        List<ExchangeRateRes> responses = exchangeRateService.syncCurrentRates();

        assertThat(responses).hasSize(3);
        assertThat(currencyRepository.count()).isEqualTo(4); // Currency에는 ExchangeRate와 달리 원화(KRW)가 초기화되어있음
        assertThat(exchangeRateRepository.count()).isEqualTo(3);

        Currency usdCurrency = currencyRepository.findByCurrencyCode(CurrencyCode.USD).orElseThrow();
        assertThat(usdCurrency.getCurrencyName()).isEqualTo("달러");
        assertThat(usdCurrency.getCountryName()).isEqualTo("미국");
        assertThat(usdCurrency.getDecimalPlaces()).isEqualTo(2);

        Currency jpyCurrency = currencyRepository.findByCurrencyCode(CurrencyCode.JPY).orElseThrow();
        assertThat(jpyCurrency.getCurrencyName()).isEqualTo("엔");
        assertThat(jpyCurrency.getCountryName()).isEqualTo("일본");
        assertThat(jpyCurrency.getDecimalPlaces()).isZero();

        Currency cnhCurrency = currencyRepository.findByCurrencyCode(CurrencyCode.CNY).orElseThrow();
        assertThat(cnhCurrency.getCurrencyName()).isEqualTo("위안");
        assertThat(cnhCurrency.getCountryName()).isEqualTo("중국");

        ExchangeRate usdRate = exchangeRateRepository.findByCurrencyCurrencyCode(CurrencyCode.USD).orElseThrow();
        assertThat(usdRate.getBasePrice()).isEqualByComparingTo("1519.50");
        assertThat(usdRate.getCurrencyUnit()).isEqualTo(1);
        assertThat(usdRate.getRateAt()).isEqualTo(LocalDateTime.of(2026, 6, 13, 12, 14, 12));

        ExchangeRate jpyRate = exchangeRateRepository.findByCurrencyCurrencyCode(CurrencyCode.JPY).orElseThrow();
        assertThat(jpyRate.getBasePrice()).isEqualByComparingTo("948.35");
        assertThat(jpyRate.getCurrencyUnit()).isEqualTo(100);
    }

    @Test
    @DisplayName("이미 존재하는 통화의 환율은 새로 추가하지 않고 업데이트한다")
    void syncCurrentRatesUpdatesExistingExchangeRate() {
        given(upbitExchangeRateClient.fetch(anyList()))
                .willReturn(List.of(rate("USD", "달러", "미국", "1519.50", 1, LocalTime.of(12, 14, 12))))
                .willReturn(List.of(rate("USD", "달러", "미국", "1520.25", 1, LocalTime.of(12, 14, 42))));

        exchangeRateService.syncCurrentRates();
        exchangeRateService.syncCurrentRates();

        assertThat(currencyRepository.count()).isEqualTo(2); // Currency에는 ExchangeRate와 달리 원화(KRW)가 초기화되어있음
        assertThat(exchangeRateRepository.count()).isEqualTo(1);

        ExchangeRate usdRate = exchangeRateRepository.findByCurrencyCurrencyCode(CurrencyCode.USD).orElseThrow();
        assertThat(usdRate.getBasePrice()).isEqualByComparingTo("1520.25");
        assertThat(usdRate.getCurrencyUnit()).isEqualTo(1);
        assertThat(usdRate.getRateAt()).isEqualTo(LocalDateTime.of(2026, 6, 13, 12, 14, 42));
    }

    @Test
    @DisplayName("환율 동기화 성공 후 DB 최신 환율을 Redis에 저장한다")
    void syncCurrentRatesStoresLatestRatesInRedisAfterCommit() {
        given(upbitExchangeRateClient.fetch(anyList()))
                .willReturn(List.of(
                        rate("USD", "달러", "미국", "1519.50", 1, LocalTime.of(12, 14, 12)),
                        rate("JPY", "엔", "일본", "948.35", 100, LocalTime.of(12, 14, 12))
                ));

        List<ExchangeRateRes> syncedRates = exchangeRateService.syncCurrentRates();

        List<ExchangeRateRes> cachedRates = exchangeRateCacheRepository.findAll();

        // cachedRates와 syncedRates의 각 ExchangeRateRes 객체 값이 필드별로 같은지 비교
        assertThat(cachedRates)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(syncedRates);
    }

    private UpbitExchangeRateRes rate(
            String currencyCode,
            String currencyName,
            String country,
            String basePrice,
            Integer currencyUnit,
            LocalTime time
    ) {
        return new UpbitExchangeRateRes(
                currencyCode,
                currencyName,
                country,
                LocalDate.of(2026, 6, 13),
                time,
                new BigDecimal(basePrice),
                currencyUnit
        );
    }
}
