package com.team10.backend.domain.investment.realtime.repository;

import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.LEADER_KEY;
import static org.assertj.core.api.Assertions.assertThat;

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
class RealtimeOrderbookLeaderLockRepositoryIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RealtimeOrderbookLeaderLockRepository leaderLockRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(LEADER_KEY);
    }

    @Test
    @DisplayName("leader lock은 한 인스턴스만 획득하고 owner만 갱신할 수 있다")
    void acquireAndRenewOnlyOwner() {
        assertThat(leaderLockRepository.tryAcquire("instance-a")).isTrue();
        assertThat(leaderLockRepository.tryAcquire("instance-b")).isFalse();
        assertThat(leaderLockRepository.isOwnedBy("instance-a")).isTrue();
        assertThat(leaderLockRepository.isOwnedBy("instance-b")).isFalse();

        assertThat(leaderLockRepository.renew("instance-a")).isTrue();
        assertThat(leaderLockRepository.renew("instance-b")).isFalse();
    }

    @Test
    @DisplayName("leader lock은 owner만 반납할 수 있고 반납 후 다른 인스턴스가 획득할 수 있다")
    void releaseOnlyOwner() {
        assertThat(leaderLockRepository.tryAcquire("instance-a")).isTrue();

        assertThat(leaderLockRepository.release("instance-b")).isFalse();
        assertThat(leaderLockRepository.isOwnedBy("instance-a")).isTrue();

        assertThat(leaderLockRepository.release("instance-a")).isTrue();
        assertThat(leaderLockRepository.tryAcquire("instance-b")).isTrue();
        assertThat(leaderLockRepository.isOwnedBy("instance-b")).isTrue();
    }
}
