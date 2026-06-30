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
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link LoginAttemptService}의 로그인 실패 관리(5회 → 30분 잠금) 검증.
 * UserServiceTest는 Mock이라 실제 임계값/예약 로직은 여기서 확인한다.
 *
 * checkAndRecordAttempt()는 "확인(GET)"과 "기록(INCR)"을 하나의 Redis 스크립트
 * (checkAndIncrIfBelowLimitScript)로 묶어 원자적으로 처리한다. 이 스크립트 자체는 Lua라서
 * 여기서는 redisTemplate.execute()의 반환값을 스텁해 서비스가 그 반환값에 맞게 반응하는지만
 * 검증한다 — 스크립트가 실제로 그 값을 내놓는지는 통합 테스트/k6(05-login-lockout-race.js)의 영역.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String EMAIL = "test@test.com";
    private static final String MAX_FAIL_COUNT_ARG = "5";
    private static final String LOCK_SECONDS_ARG = "1800";

    @Mock StringRedisTemplate redisTemplate;
    @Mock RedisScript<Long> checkAndIncrIfBelowLimitScript;

    LoginAttemptService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new LoginAttemptService(redisTemplate, checkAndIncrIfBelowLimitScript);
    }

    @Nested
    @DisplayName("checkAndRecordAttempt")
    class CheckAndRecordAttempt {

        @Test
        @DisplayName("스크립트가 양수(예약된 새 카운트)를 반환하면 통과")
        void scriptReturnsPositiveCount_doesNotThrow() {
            stubScript(3L);

            assertThatCode(() -> service.checkAndRecordAttempt(EMAIL)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("스크립트가 -1(이미 임계값 이상이라 증가 안 함)을 반환하면 LOGIN_LOCKED")
        void scriptReturnsSentinel_throwsLoginLocked() {
            stubScript(-1L);

            assertThatThrownBy(() -> service.checkAndRecordAttempt(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.LOGIN_LOCKED);
        }

        @Test
        @DisplayName("스크립트 실행 결과가 null이면(연결 문제 등) 방어적으로 LOGIN_LOCKED")
        void scriptReturnsNull_throwsLoginLockedDefensively() {
            stubScript(null);

            assertThatThrownBy(() -> service.checkAndRecordAttempt(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.LOGIN_LOCKED);
        }

        @Test
        @DisplayName("이메일별 키로 스크립트를 실행하고, 임계값(5)·잠금시간(1800초)을 인자로 넘긴다")
        void executesScript_withMaxFailCountAndLockDurationArgs() {
            stubScript(1L);

            service.checkAndRecordAttempt(EMAIL);

            verify(redisTemplate).execute(
                    eq(checkAndIncrIfBelowLimitScript),
                    eq(List.of(FAIL_KEY_PREFIX + EMAIL)),
                    eq(MAX_FAIL_COUNT_ARG),
                    eq(LOCK_SECONDS_ARG));
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
    @DisplayName("시나리오: 1~5번째 시도는 예약되고, 6번째(스크립트가 이미 5 이상으로 보고 -1 반환)부터 잠긴다")
    void scenario_locksFromSixthAttempt() {
        for (long count = 1; count <= 5; count++) {
            stubScript(count);
            assertThatCode(() -> service.checkAndRecordAttempt(EMAIL)).doesNotThrowAnyException();
        }

        stubScript(-1L);
        assertThatThrownBy(() -> service.checkAndRecordAttempt(EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.LOGIN_LOCKED);
    }

    private void stubScript(Long returnValue) {
        when(redisTemplate.execute(
                eq(checkAndIncrIfBelowLimitScript),
                eq(List.of(FAIL_KEY_PREFIX + EMAIL)),
                eq(MAX_FAIL_COUNT_ARG),
                eq(LOCK_SECONDS_ARG)))
                .thenReturn(returnValue);
    }
}
