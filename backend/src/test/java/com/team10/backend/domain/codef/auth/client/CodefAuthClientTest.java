package com.team10.backend.domain.codef.auth.client;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodefAuthClientTest {

    @Mock CodefOAuthExchange codefOAuthExchange;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock RedisScript<Long> getAndDeleteIfMatchScript;

    CodefAuthClient codefAuthClient;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 기본값: 분산 락은 항상 즉시 획득되고, Redis 공유 캐시는 비어있다고 가정.
        // (각 테스트가 필요한 부분만 덮어써서 의도를 드러낸다)
        // 락 값은 매 호출마다 새로 생성되는 UUID이므로 고정값("1") 대신 anyString()으로 매칭한다.
        lenient().when(valueOperations.setIfAbsent(eq("codef:oauth:token:lock:test"), anyString(), any(Duration.class)))
                .thenReturn(true);
        lenient().when(valueOperations.get("codef:oauth:token:test")).thenReturn(null);

        codefAuthClient = new CodefAuthClient(
                "test", "test-client-id", "test-client-secret", codefOAuthExchange, redisTemplate, getAndDeleteIfMatchScript);
    }

    @Nested
    @DisplayName("getAccessToken")
    class GetAccessToken {

        @Test
        @DisplayName("최초 호출 — OAuth API를 호출해 토큰을 발급받고 로컬+Redis에 캐싱한다")
        void firstCall_fetchesAndCachesToken() {
            when(codefOAuthExchange.issueToken(anyString(), anyString()))
                    .thenReturn(new CodefOAuthExchange.CodefTokenResponse("abc123", 3600L));

            String token = codefAuthClient.getAccessToken();

            assertThat(token).isEqualTo("abc123");
            verify(codefOAuthExchange, times(1)).issueToken(anyString(), anyString());
            // 만료 5분 버퍼를 뺀 나머지(3600-300=3300초)만큼 Redis에 공유 캐싱
            verify(valueOperations).set(eq("codef:oauth:token:test"), eq("abc123"), eq(Duration.ofSeconds(3300)));
        }

        @Test
        @DisplayName("캐시 유효기간 내 재호출 — 로컬 캐시(L1)를 그대로 반환하고 API/Redis 모두 다시 안 탄다")
        void secondCall_withinValidity_usesCachedToken() {
            when(codefOAuthExchange.issueToken(anyString(), anyString()))
                    .thenReturn(new CodefOAuthExchange.CodefTokenResponse("abc123", 3600L));

            String first = codefAuthClient.getAccessToken();
            clearInvocations(valueOperations);
            String second = codefAuthClient.getAccessToken();

            assertThat(first).isEqualTo(second);
            verify(codefOAuthExchange, times(1)).issueToken(anyString(), anyString()); // 두 번째 호출은 L1 캐시 재사용
            verifyNoInteractions(valueOperations); // L1이 유효하면 Redis는 아예 건드리지 않음
        }

        @Test
        @DisplayName("Redis 공유 캐시(L2)에 다른 인스턴스가 올려둔 토큰이 있으면 그걸 재사용한다")
        void sharedCacheHit_doesNotCallOAuthApi() {
            when(valueOperations.get("codef:oauth:token:test")).thenReturn("shared-token");
            when(redisTemplate.getExpire("codef:oauth:token:test")).thenReturn(1000L);

            String token = codefAuthClient.getAccessToken();

            assertThat(token).isEqualTo("shared-token");
            verify(codefOAuthExchange, never()).issueToken(anyString(), anyString());
        }

        @Test
        @DisplayName("다른 인스턴스가 분산 락을 잡고 있으면 대기하다가 Redis에 올라온 토큰을 재사용한다")
        void lockContention_waitsAndReusesTokenFromOtherInstance() {
            when(valueOperations.setIfAbsent(eq("codef:oauth:token:lock:test"), anyString(), any(Duration.class)))
                    .thenReturn(false); // 락 획득 실패 — 다른 인스턴스가 발급 중
            when(valueOperations.get("codef:oauth:token:test"))
                    .thenReturn(null, "other-instance-token"); // 첫 확인은 미스, 대기 후 재확인 시 히트
            when(redisTemplate.getExpire("codef:oauth:token:test")).thenReturn(1000L);

            String token = codefAuthClient.getAccessToken();

            assertThat(token).isEqualTo("other-instance-token");
            verify(codefOAuthExchange, never()).issueToken(anyString(), anyString());
        }

        @Test
        @DisplayName("락 경쟁 후 대기 시간을 초과하면 가용성을 위해 직접 발급한다")
        void lockContention_timesOutAndFetchesDirectly() {
            when(valueOperations.setIfAbsent(eq("codef:oauth:token:lock:test"), anyString(), any(Duration.class)))
                    .thenReturn(false); // 끝까지 락을 못 잡음
            when(valueOperations.get("codef:oauth:token:test")).thenReturn(null); // 끝까지 Redis에도 안 올라옴
            when(codefOAuthExchange.issueToken(anyString(), anyString()))
                    .thenReturn(new CodefOAuthExchange.CodefTokenResponse("direct-fetch", 3600L));

            String token = codefAuthClient.getAccessToken();

            assertThat(token).isEqualTo("direct-fetch");
            verify(codefOAuthExchange, times(1)).issueToken(anyString(), anyString());
        }

        @Test
        @DisplayName("만료 5분 이내로 남은 토큰은 Redis에 공유하지 않고, 다음 호출에서 재발급한다")
        void nearExpiry_refetchesToken() {
            // expires_in=60s → 버퍼(5분)보다 짧아 즉시 만료로 간주되어야 함
            when(codefOAuthExchange.issueToken(anyString(), anyString()))
                    .thenReturn(new CodefOAuthExchange.CodefTokenResponse("short-lived", 60L));
            String first = codefAuthClient.getAccessToken();

            when(codefOAuthExchange.issueToken(anyString(), anyString()))
                    .thenReturn(new CodefOAuthExchange.CodefTokenResponse("refreshed", 3600L));
            String second = codefAuthClient.getAccessToken();

            assertThat(first).isEqualTo("short-lived");
            assertThat(second).isEqualTo("refreshed");
            verify(codefOAuthExchange, times(2)).issueToken(anyString(), anyString());
            verify(valueOperations, never()).set(eq("codef:oauth:token:test"), eq("short-lived"), any(Duration.class));
        }

        @Test
        @DisplayName("4xx/5xx 응답(CodefHttpServiceConfig의 defaultStatusHandler가 이미 변환) — BusinessException을 감싸지 않고 그대로 전파한다")
        void exchangeThrowsBusinessException_propagatesAsIs() {
            when(codefOAuthExchange.issueToken(anyString(), anyString()))
                    .thenThrow(new BusinessException(UserErrorCode.CODEF_TOKEN_ISSUE_FAILED));

            assertThatThrownBy(() -> codefAuthClient.getAccessToken())
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.CODEF_TOKEN_ISSUE_FAILED);
        }

        @Test
        @DisplayName("네트워크 단계 등 예기치 못한 예외는 감싸지 않고 그대로 전파한다")
        void exchangeThrowsUnexpectedException_propagatesAsIs() {
            RuntimeException networkError = new RuntimeException("network error");
            when(codefOAuthExchange.issueToken(anyString(), anyString()))
                    .thenThrow(networkError);

            assertThatThrownBy(() -> codefAuthClient.getAccessToken())
                    .isSameAs(networkError);
        }

        @Test
        @DisplayName("expires_in 필드가 없으면 CodefAuthException으로 변환된다")
        void missingExpiresInField_throwsCodefAuthException() {
            when(codefOAuthExchange.issueToken(anyString(), anyString()))
                    .thenReturn(new CodefOAuthExchange.CodefTokenResponse("abc123", null));

            assertThatThrownBy(() -> codefAuthClient.getAccessToken())
                    .isInstanceOf(CodefAuthException.class);
        }

        @Test
        @DisplayName("access_token 필드가 없으면 CodefAuthException으로 변환된다")
        void missingAccessTokenField_throwsCodefAuthException() {
            when(codefOAuthExchange.issueToken(anyString(), anyString()))
                    .thenReturn(new CodefOAuthExchange.CodefTokenResponse(null, 3600L));

            assertThatThrownBy(() -> codefAuthClient.getAccessToken())
                    .isInstanceOf(CodefAuthException.class);
        }

        @Test
        @DisplayName("락 해제는 무조건 DEL이 아니라 내가 쓴 값일 때만 지우는 compare-and-delete 스크립트를 사용한다")
        void releasesLock_viaCompareAndDeleteScript_notUnconditionalDelete() {
            when(codefOAuthExchange.issueToken(anyString(), anyString()))
                    .thenReturn(new CodefOAuthExchange.CodefTokenResponse("abc123", 3600L));

            codefAuthClient.getAccessToken();

            // TTL 만료로 락이 다른 인스턴스로 넘어간 경우를 보호하려면 값 비교 없는 무조건 DEL을 쓰면 안 된다.
            verify(redisTemplate).execute(eq(getAndDeleteIfMatchScript), eq(List.of("codef:oauth:token:lock:test")), anyString());
            verify(redisTemplate, never()).delete("codef:oauth:token:lock:test");
        }
    }
}
