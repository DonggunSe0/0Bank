package com.team10.backend.domain.user.service;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** {@link LoginAttemptService}의 로그인 실패 관리(5회 → 30분 잠금) 검증. UserServiceTest는 Mock이라 실제 임계값/TTL 로직은 여기서 확인한다. */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String EMAIL = "test@test.com";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock RedisScript<Long> incrAndExpireScript;

    LoginAttemptService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new LoginAttemptService(redisTemplate, incrAndExpireScript);
    }

    @Nested
    @DisplayName("checkAndThrowIfLocked")
    class CheckAndThrowIfLocked {

        @Test
        @DisplayName("실패 기록 없음(null) → 통과")
        void noRecord_doesNotThrow() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(FAIL_KEY_PREFIX + EMAIL)).thenReturn(null);

            assertThatCode(() -> service.checkAndThrowIfLocked(EMAIL)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패 횟수가 임계값(5) 미만 → 통과")
        void underThreshold_doesNotThrow() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(FAIL_KEY_PREFIX + EMAIL)).thenReturn("4");

            assertThatCode(() -> service.checkAndThrowIfLocked(EMAIL)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패 횟수가 정확히 임계값(5) → LOGIN_LOCKED")
        void atThreshold_throwsLoginLocked() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(FAIL_KEY_PREFIX + EMAIL)).thenReturn("5");

            assertThatThrownBy(() -> service.checkAndThrowIfLocked(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.LOGIN_LOCKED);
        }

        @Test
        @DisplayName("실패 횟수가 임계값을 초과 → LOGIN_LOCKED (방어적 처리)")
        void overThreshold_throwsLoginLocked() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(FAIL_KEY_PREFIX + EMAIL)).thenReturn("7");

            assertThatThrownBy(() -> service.checkAndThrowIfLocked(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.LOGIN_LOCKED);
        }
    }

    @Nested
    @DisplayName("recordFailure")
    class RecordFailure {

        @Test
        @DisplayName("이메일별 키로 INCR+EXPIRE 스크립트를 실행하고, TTL은 잠금 시간(30분=1800초)과 같다")
        void executesIncrAndExpireScript_withLockDurationTtl() {
            when(redisTemplate.execute(eq(incrAndExpireScript), eq(List.of(FAIL_KEY_PREFIX + EMAIL)), any(String.class)))
                    .thenReturn(1L);

            service.recordFailure(EMAIL);

            verify(redisTemplate).execute(
                    eq(incrAndExpireScript),
                    eq(List.of(FAIL_KEY_PREFIX + EMAIL)),
                    eq("1800"));
        }
    }

    @Nested
    @DisplayName("clearFailures")
    class ClearFailures {

        @Test
        @DisplayName("실패 카운터 키를 삭제한다")
        void deletesFailureCounterKey() {
            service.clearFailures(EMAIL);

            verify(redisTemplate).delete(FAIL_KEY_PREFIX + EMAIL);
        }
    }

    @Test
    @DisplayName("시나리오: 4회 실패까지는 통과, 5회째부터 잠긴다")
    void scenario_locksExactlyAtFifthFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        for (int fail = 1; fail <= 4; fail++) {
            when(valueOperations.get(FAIL_KEY_PREFIX + EMAIL)).thenReturn(String.valueOf(fail));
            assertThat(catchLockedOrNull()).isNull();
        }

        when(valueOperations.get(FAIL_KEY_PREFIX + EMAIL)).thenReturn("5");
        assertThatThrownBy(() -> service.checkAndThrowIfLocked(EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.LOGIN_LOCKED);
    }

    private BusinessException catchLockedOrNull() {
        try {
            service.checkAndThrowIfLocked(EMAIL);
            return null;
        } catch (BusinessException e) {
            return e;
        }
    }
}
