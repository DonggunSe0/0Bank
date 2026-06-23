package com.team10.backend.global.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** {@link TokenBlocklistService}의 AT 블랙리스트(로그아웃 등 즉시 무효화) 검증. UserServiceTest는 Mock이라 TTL/조회 로직은 여기서 확인한다. */
@ExtendWith(MockitoExtension.class)
class TokenBlocklistServiceTest {

    private static final String BLOCKLIST_PREFIX = "blocklist:";
    private static final String JTI = "jti-123";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    TokenBlocklistService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new TokenBlocklistService(redisTemplate);
    }

    @Nested
    @DisplayName("block")
    class Block {

        @Test
        @DisplayName("잔여 만료시간(초)을 TTL로 사용해 jti를 등록한다")
        void registersJti_withRemainingSecondsAsTtl() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            service.block(JTI, 3600L);

            verify(valueOperations).set(eq(BLOCKLIST_PREFIX + JTI), eq("1"), eq(Duration.ofSeconds(3600L)));
        }

        @Test
        @DisplayName("잔여 만료시간이 0이면 저장하지 않는다 (이미 만료된 토큰)")
        void zeroRemainingSeconds_doesNotStore() {
            service.block(JTI, 0L);

            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("잔여 만료시간이 음수면 저장하지 않는다")
        void negativeRemainingSeconds_doesNotStore() {
            service.block(JTI, -10L);

            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("isBlocked")
    class IsBlocked {

        @Test
        @DisplayName("블랙리스트에 등록된 jti → true")
        void registeredJti_returnsTrue() {
            when(redisTemplate.hasKey(BLOCKLIST_PREFIX + JTI)).thenReturn(true);

            assertThat(service.isBlocked(JTI)).isTrue();
        }

        @Test
        @DisplayName("블랙리스트에 없는 jti → false")
        void unregisteredJti_returnsFalse() {
            when(redisTemplate.hasKey(BLOCKLIST_PREFIX + JTI)).thenReturn(false);

            assertThat(service.isBlocked(JTI)).isFalse();
        }

        @Test
        @DisplayName("Redis 조회 결과가 null이어도 false (NPE 없이 안전하게 처리)")
        void nullHasKeyResult_returnsFalseSafely() {
            when(redisTemplate.hasKey(BLOCKLIST_PREFIX + JTI)).thenReturn(null);

            assertThat(service.isBlocked(JTI)).isFalse();
        }
    }
}
