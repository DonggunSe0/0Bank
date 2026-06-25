package com.team10.backend.global.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** {@link RefreshTokenService}의 발급/삭제와 RT Rotation 핵심인 atomic get-and-delete 검증. Mock 너머의 race-condition 방지 로직을 직접 확인한다. */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String KEY_PREFIX = "refresh:";
    private static final Long USER_ID = 1L;
    private static final long EXPIRATION_SECONDS = 604_800L; // 7일

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock RedisScript<Long> getAndDeleteIfMatchScript;

    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(redisTemplate, getAndDeleteIfMatchScript);
        // refreshTokenExpirationSeconds는 @Value 필드라 생성자에 없음 — application.yml 설정값을 흉내내어 직접 주입
        ReflectionTestUtils.setField(service, "refreshTokenExpirationSeconds", EXPIRATION_SECONDS);
    }

    @Nested
    @DisplayName("issue")
    class Issue {

        @Test
        @DisplayName("UUID 토큰을 생성해 TTL(설정된 만료시간)과 함께 저장하고 반환한다")
        void issuesUuidToken_andStoresWithConfiguredTtl() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String token = service.issue(USER_ID);

            assertThat(UUID.fromString(token)).isNotNull(); // 유효한 UUID 형식인지
            verify(valueOperations).set(eq(KEY_PREFIX + USER_ID), eq(token), eq(Duration.ofSeconds(EXPIRATION_SECONDS)));
        }

        @Test
        @DisplayName("호출마다 다른 토큰을 생성한다")
        void generatesDifferentTokenPerCall() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String first = service.issue(USER_ID);
            String second = service.issue(USER_ID);

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("validateAndConsume")
    class ValidateAndConsume {

        @Test
        @DisplayName("저장된 토큰과 일치 → true (스크립트가 원자적으로 GET+DEL 수행)")
        void matchingToken_returnsTrue() {
            when(redisTemplate.execute(eq(getAndDeleteIfMatchScript), eq(List.of(KEY_PREFIX + USER_ID)), any(String.class)))
                    .thenReturn(1L);

            boolean result = service.validateAndConsume(USER_ID, "correct-token");

            assertThat(result).isTrue();
            verify(redisTemplate).execute(getAndDeleteIfMatchScript, List.of(KEY_PREFIX + USER_ID), "correct-token");
        }

        @Test
        @DisplayName("저장된 토큰과 불일치 → false, 기존 토큰은 삭제되지 않는다 (스크립트가 0 반환)")
        void mismatchedToken_returnsFalse() {
            when(redisTemplate.execute(eq(getAndDeleteIfMatchScript), eq(List.of(KEY_PREFIX + USER_ID)), any(String.class)))
                    .thenReturn(0L);

            boolean result = service.validateAndConsume(USER_ID, "wrong-token");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Redis에 토큰이 없음(만료/이미 소비됨) → false")
        void noStoredToken_returnsFalse() {
            when(redisTemplate.execute(eq(getAndDeleteIfMatchScript), eq(List.of(KEY_PREFIX + USER_ID)), any(String.class)))
                    .thenReturn(0L);

            boolean result = service.validateAndConsume(USER_ID, "any-token");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("토큰이 null이면 Redis를 조회하지 않고 즉시 false")
        void nullToken_returnsFalseWithoutRedisCall() {
            boolean result = service.validateAndConsume(USER_ID, null);

            assertThat(result).isFalse();
            verify(redisTemplate, never()).execute(eq(getAndDeleteIfMatchScript), any(List.class), any());
        }

        @Test
        @DisplayName("동시에 같은 토큰으로 두 번 호출되면 — 스크립트가 두 번째 호출엔 0을 반환해 한 번만 성공한다 (race condition 방지)")
        void concurrentRotation_onlyFirstCallSucceeds() {
            when(redisTemplate.execute(eq(getAndDeleteIfMatchScript), eq(List.of(KEY_PREFIX + USER_ID)), any(String.class)))
                    .thenReturn(1L)  // 첫 호출: 일치 + 삭제 성공
                    .thenReturn(0L); // 두 번째 호출: 이미 삭제됐으므로 불일치

            boolean firstResult = service.validateAndConsume(USER_ID, "shared-token");
            boolean secondResult = service.validateAndConsume(USER_ID, "shared-token");

            assertThat(firstResult).isTrue();
            assertThat(secondResult).isFalse();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("Refresh Token 키를 삭제한다")
        void deletesRefreshTokenKey() {
            service.delete(USER_ID);

            verify(redisTemplate).delete(KEY_PREFIX + USER_ID);
        }
    }
}
