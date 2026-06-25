package com.team10.backend.domain.exchange.repository;

import com.team10.backend.domain.exchange.dto.res.ExchangeRateRes;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class ExchangeRateCacheRepositoryIntegrationTest {

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
    private ExchangeRateCacheRepository exchangeRateCacheRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(CACHE_KEY);
    }

    @Test
    @DisplayName("환율 목록을 Redis Hash에 저장하고 전체 조회한다")
    void saveAllAndFindAll() {
        ExchangeRateRes usd = rate(1L, CurrencyCode.USD, "1519.50", 1);
        ExchangeRateRes jpy = rate(2L, CurrencyCode.JPY, "948.35", 100);

        exchangeRateCacheRepository.saveAll(List.of(usd, jpy));

        List<ExchangeRateRes> rates = exchangeRateCacheRepository.findAll();

        assertThat(rates)
                .hasSize(2)
                .extracting(
                        ExchangeRateRes::exchangeRateId,
                        ExchangeRateRes::currencyCode,
                        ExchangeRateRes::basePrice,
                        ExchangeRateRes::currencyUnit,
                        ExchangeRateRes::rateAt
                )
                .containsExactlyInAnyOrder(
                        tuple(1L, CurrencyCode.USD, new BigDecimal("1519.50"), 1, LocalDateTime.of(2026, 6, 13, 12, 14, 12)),
                        tuple(2L, CurrencyCode.JPY, new BigDecimal("948.35"), 100, LocalDateTime.of(2026, 6, 13, 12, 14, 12))
                );

        assertThat(redisTemplate.opsForHash().size(CACHE_KEY)).isEqualTo(2);
    }

    @Test
    @DisplayName("단건 환율을 Redis Hash에 저장하고 통화 코드로 조회한다")
    void saveAndFindByCurrency() {
        ExchangeRateRes usd = rate(1L, CurrencyCode.USD, "1519.50", 1);

        exchangeRateCacheRepository.save(usd);

        Optional<ExchangeRateRes> found = exchangeRateCacheRepository.findByCurrency(CurrencyCode.USD);
        Optional<ExchangeRateRes> missing = exchangeRateCacheRepository.findByCurrency(CurrencyCode.JPY);

        assertThat(found).contains(usd);
        assertThat(missing).isEmpty();
    }

    private ExchangeRateRes rate(Long id, CurrencyCode currencyCode, String basePrice, Integer currencyUnit) {
        return new ExchangeRateRes(
                id,
                currencyCode,
                new BigDecimal(basePrice),
                currencyUnit,
                LocalDateTime.of(2026, 6, 13, 12, 14, 12)
        );
    }
}
