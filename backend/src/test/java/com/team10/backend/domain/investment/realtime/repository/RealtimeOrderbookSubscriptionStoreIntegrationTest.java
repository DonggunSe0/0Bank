package com.team10.backend.domain.investment.realtime.repository;

import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.ACTIVE_STOCKS_KEY;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.ALL_KEYS_PATTERN;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.STOCK_STREAMS_KEY_PREFIX;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.STREAMS_KEY_SUFFIX;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.USER_STREAMS_KEY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class RealtimeOrderbookSubscriptionStoreIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RealtimeOrderbookSubscriptionStore subscriptionStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys(ALL_KEYS_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("실시간 호가 구독 상태를 저장하고 streamId로 조회한다")
    void saveAndFindByStreamId() {
        RealtimeOrderbookSubscription subscription = subscription(
                "stream-1",
                1L,
                "005930",
                "instance-a"
        );

        save(subscription);

        assertThat(subscriptionStore.findByStreamId("stream-1"))
                .contains(subscription);
        assertThat(subscriptionStore.countActiveStreamsByStockCode("005930"))
                .isEqualTo(1);
        assertThat(subscriptionStore.findActiveStreamIdsByUserId(1L))
                .containsExactly("stream-1");
    }

    @Test
    @DisplayName("같은 종목을 구독하는 여러 스트림을 종목 기준으로 집계한다")
    void countActiveStreamsByStockCode() {
        save(subscription("stream-1", 1L, "005930", "instance-a"));
        save(subscription("stream-2", 2L, "005930", "instance-b"));
        save(subscription("stream-3", 1L, "000660", "instance-a"));

        assertThat(subscriptionStore.countActiveStreamsByStockCode("005930"))
                .isEqualTo(2);
        assertThat(subscriptionStore.findActiveStockCodes())
                .containsExactlyInAnyOrder("005930", "000660");
    }

    @Test
    @DisplayName("KIS 구독 종목 제한에 도달하면 새로운 종목 저장을 거부한다")
    void saveIfWithinActiveStockLimitRejectsNewStockWhenLimitReached() {
        assertThat(subscriptionStore.saveIfWithinActiveStockLimit(
                subscription("stream-1", 1L, "005930", "instance-a"),
                1
        )).isTrue();

        assertThat(subscriptionStore.saveIfWithinActiveStockLimit(
                subscription("stream-2", 2L, "000660", "instance-a"),
                1
        )).isFalse();

        assertThat(subscriptionStore.findByStreamId("stream-2")).isEmpty();
        assertThat(subscriptionStore.findActiveStockCodes()).containsExactly("005930");
    }

    @Test
    @DisplayName("KIS 구독 종목 제한에 도달했어도 이미 활성 구독 중인 종목은 저장한다")
    void saveIfWithinActiveStockLimitAllowsAlreadyActiveStockWhenLimitReached() {
        assertThat(subscriptionStore.saveIfWithinActiveStockLimit(
                subscription("stream-1", 1L, "005930", "instance-a"),
                1
        )).isTrue();

        assertThat(subscriptionStore.saveIfWithinActiveStockLimit(
                subscription("stream-2", 2L, "005930", "instance-b"),
                1
        )).isTrue();

        assertThat(subscriptionStore.countActiveStreamsByStockCode("005930"))
                .isEqualTo(2);
        assertThat(subscriptionStore.findActiveStockCodes()).containsExactly("005930");
    }

    @Test
    @DisplayName("동시에 여러 새 종목 저장을 시도해도 KIS 구독 종목 제한을 초과하지 않는다")
    void saveIfWithinActiveStockLimitPreventsConcurrentLimitOverflow() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(saveAfterStart(
                    startLatch,
                    subscription("stream-1", 1L, "005930", "instance-a"),
                    1
            ));
            Future<Boolean> second = executor.submit(saveAfterStart(
                    startLatch,
                    subscription("stream-2", 2L, "000660", "instance-b"),
                    1
            ));

            startLatch.countDown();

            List<Boolean> results = List.of(
                    first.get(3, TimeUnit.SECONDS),
                    second.get(3, TimeUnit.SECONDS)
            );

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(subscriptionStore.findActiveStockCodes()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("streamId 삭제 시 사용자/종목 인덱스에서도 제거한다")
    void deleteByStreamId() {
        save(subscription("stream-1", 1L, "005930", "instance-a"));

        assertThat(subscriptionStore.deleteByStreamId("stream-1"))
                .contains(subscription("stream-1", 1L, "005930", "instance-a"));

        assertThat(subscriptionStore.findByStreamId("stream-1")).isEmpty();
        assertThat(subscriptionStore.countActiveStreamsByStockCode("005930")).isZero();
        assertThat(subscriptionStore.findActiveStreamIdsByUserId(1L)).isEmpty();
        assertThat(subscriptionStore.findActiveStockCodes()).isEmpty();
    }

    @Test
    @DisplayName("streamId와 사용자 ID가 일치할 때만 구독 상태를 삭제한다")
    void deleteByStreamIdAndUserId() {
        save(subscription("stream-1", 1L, "005930", "instance-a"));

        assertThat(subscriptionStore.deleteByStreamIdAndUserId("stream-1", 2L))
                .isEmpty();
        assertThat(subscriptionStore.findByStreamId("stream-1"))
                .contains(subscription("stream-1", 1L, "005930", "instance-a"));

        assertThat(subscriptionStore.deleteByStreamIdAndUserId("stream-1", 1L))
                .contains(subscription("stream-1", 1L, "005930", "instance-a"));
        assertThat(subscriptionStore.findByStreamId("stream-1")).isEmpty();
        assertThat(subscriptionStore.findActiveStockCodes()).isEmpty();
    }

    @Test
    @DisplayName("lease가 사라진 stale stream은 활성 구독 조회에서 제외하고 인덱스에서 제거한다")
    void staleStreamIsFilteredAndRemoved() {
        save(subscription("stream-active", 1L, "005930", "instance-a"));
        redisTemplate.opsForSet().add(stockStreamsKey("005930"), "stream-stale");
        redisTemplate.opsForSet().add(userStreamsKey(1L), "stream-stale");

        assertThat(subscriptionStore.countActiveStreamsByStockCode("005930"))
                .isEqualTo(1);
        assertThat(subscriptionStore.findActiveStreamIdsByUserId(1L))
                .containsExactly("stream-active");
        assertThat(redisTemplate.opsForSet().members(stockStreamsKey("005930")))
                .doesNotContain("stream-stale");
        assertThat(redisTemplate.opsForSet().members(userStreamsKey(1L)))
                .doesNotContain("stream-stale");
    }

    @Test
    @DisplayName("활성 종목 인덱스에만 남은 stale 종목은 전체 활성 종목 조회 시 제거한다")
    void findActiveStockCodesRemovesStaleActiveStockIndex() {
        redisTemplate.opsForSet().add(ACTIVE_STOCKS_KEY, "005930");

        assertThat(subscriptionStore.findActiveStockCodes()).isEmpty();
        assertThat(redisTemplate.opsForSet().members(ACTIVE_STOCKS_KEY))
                .doesNotContain("005930");
    }

    @Test
    @DisplayName("존재하는 streamId의 lease를 갱신하고 삭제된 streamId는 갱신하지 않는다")
    void renewLease() {
        save(subscription("stream-1", 1L, "005930", "instance-a"));

        assertThat(subscriptionStore.renewLease("stream-1")).isTrue();

        subscriptionStore.deleteByStreamId("stream-1");

        assertThat(subscriptionStore.renewLease("stream-1")).isFalse();
    }

    private Callable<Boolean> saveAfterStart(
            CountDownLatch startLatch,
            RealtimeOrderbookSubscription subscription,
            int maxActiveStockCount
    ) {
        return () -> {
            startLatch.await();
            return subscriptionStore.saveIfWithinActiveStockLimit(subscription, maxActiveStockCount);
        };
    }

    private void save(RealtimeOrderbookSubscription subscription) {
        assertThat(subscriptionStore.saveIfWithinActiveStockLimit(subscription, 41)).isTrue();
    }

    private RealtimeOrderbookSubscription subscription(
            String streamId,
            Long userId,
            String stockCode,
            String ownerInstanceId
    ) {
        return new RealtimeOrderbookSubscription(streamId, userId, stockCode, ownerInstanceId);
    }

    private String stockStreamsKey(String stockCode) {
        return STOCK_STREAMS_KEY_PREFIX + stockCode + STREAMS_KEY_SUFFIX;
    }

    private String userStreamsKey(Long userId) {
        return USER_STREAMS_KEY_PREFIX + userId + STREAMS_KEY_SUFFIX;
    }
}
