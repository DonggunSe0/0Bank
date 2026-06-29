package com.team10.backend.domain.user.verification;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OneWonVerificationServiceTest {

    private static final String LOCK_PREFIX = "identity:one-won:lock:";
    private static final String KEY_PREFIX = "identity:one-won:";
    private static final String ATTEMPT_PREFIX = "identity:one-won:attempt:";
    private static final String DAILY_PREFIX = "identity:one-won:daily:";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    OneWonVerificationService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // RedisScriptConfig가 빈으로 등록하는 스크립트와 동일한 내용 — 생성자 시그니처 변경(필드 → DI)에 맞춰 직접 주입
        RedisScript<Long> incrWithExpireIfNewScript = RedisScript.of(
                "local v = redis.call('INCR', KEYS[1])\n" +
                "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
                "return v",
                Long.class
        );
        RedisScript<Long> incrAndExpireScript = RedisScript.of(
                "local v = redis.call('INCR', KEYS[1])\n" +
                "redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
                "return v",
                Long.class
        );
        RedisScript<Long> getAndDeleteIfMatchScript = RedisScript.of(
                "local stored = redis.call('GET', KEYS[1])\n" +
                "if stored == ARGV[1] then\n" +
                "  redis.call('DEL', KEYS[1])\n" +
                "  return 1\n" +
                "else\n" +
                "  return 0\n" +
                "end",
                Long.class
        );
        service = new OneWonVerificationService(
                redisTemplate, incrWithExpireIfNewScript, incrAndExpireScript, getAndDeleteIfMatchScript);
    }

    @Nested
    @DisplayName("tryAcquireStartLock")
    class TryAcquireStartLock {

        @Test
        @DisplayName("락 획득 성공 → true")
        void acquired_returnsTrue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(LOCK_PREFIX + 1L), eq("1"), any(Duration.class)))
                    .thenReturn(true);

            assertThat(service.tryAcquireStartLock(1L)).isTrue();
        }

        @Test
        @DisplayName("이미 처리 중(락 획득 실패) → false")
        void notAcquired_returnsFalse() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(LOCK_PREFIX + 1L), eq("1"), any(Duration.class)))
                    .thenReturn(false);

            assertThat(service.tryAcquireStartLock(1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("releaseStartLock")
    class ReleaseStartLock {

        @Test
        @DisplayName("락 키를 삭제한다")
        void deletesLockKey() {
            service.releaseStartLock(1L);

            verify(redisTemplate).delete(LOCK_PREFIX + 1L);
        }
    }

    @Nested
    @DisplayName("generateAndStore")
    class GenerateAndStore {

        @Test
        @DisplayName("한도 이내면 4자리 코드를 생성해 저장하고 반환한다")
        void withinLimit_generatesAndStoresCode() {
            when(redisTemplate.execute(any(RedisScript.class), eq(List.of(DAILY_PREFIX + 1L)), any(Object[].class)))
                    .thenReturn(1L);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String code = service.generateAndStore(10L, 1L);

            assertThat(code).matches("\\d{4}");
            verify(valueOperations).set(eq(KEY_PREFIX + 10L), eq(code), any(Duration.class));
        }

        @Test
        @DisplayName("일일 카운터 결과가 null이면 INTERNAL_SERVER_ERROR")
        void nullDailyCount_throwsInternalServerError() {
            when(redisTemplate.execute(any(RedisScript.class), eq(List.of(DAILY_PREFIX + 1L)), any(Object[].class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> service.generateAndStore(10L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("하루 요청 한도를 초과하면 ONE_WON_DAILY_LIMIT_EXCEEDED")
        void exceedsDailyLimit_throwsBusinessException() {
            when(redisTemplate.execute(any(RedisScript.class), eq(List.of(DAILY_PREFIX + 1L)), any(Object[].class)))
                    .thenReturn(11L);

            assertThatThrownBy(() -> service.generateAndStore(10L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_DAILY_LIMIT_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("deleteCode")
    class DeleteCode {

        @Test
        @DisplayName("인증 코드 키를 삭제한다")
        void deletesCodeKey() {
            service.deleteCode(10L);

            verify(redisTemplate).delete(KEY_PREFIX + 10L);
        }
    }

    @Nested
    @DisplayName("decrementDailyCount")
    class DecrementDailyCount {

        @Test
        @DisplayName("일일 카운터를 감소시킨다")
        void decrementsDailyCounter() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            service.decrementDailyCount(1L);

            verify(valueOperations).decrement(DAILY_PREFIX + 1L);
        }
    }

    @Nested
    @DisplayName("verify")
    class Verify {

        @Test
        @DisplayName("저장된 코드가 없으면 EXPIRED")
        void noStoredCode_returnsExpired() {
            when(redisTemplate.execute(any(RedisScript.class), eq(List.of(KEY_PREFIX + 10L)), any(Object[].class)))
                    .thenReturn(0L);
            when(redisTemplate.hasKey(KEY_PREFIX + 10L)).thenReturn(false);

            OneWonVerificationService.VerifyResult result = service.verify(10L, "1234");

            assertThat(result).isEqualTo(OneWonVerificationService.VerifyResult.EXPIRED);
        }

        @Test
        @DisplayName("입력 코드가 일치하면 MATCHED, 시도 카운터 키를 삭제한다(코드 키는 스크립트가 원자적으로 삭제)")
        void matches_returnsMatchedAndDeletesAttemptKey() {
            when(redisTemplate.execute(any(RedisScript.class), eq(List.of(KEY_PREFIX + 10L)), any(Object[].class)))
                    .thenReturn(1L);

            OneWonVerificationService.VerifyResult result = service.verify(10L, "1234");

            assertThat(result).isEqualTo(OneWonVerificationService.VerifyResult.MATCHED);
            verify(redisTemplate).delete(ATTEMPT_PREFIX + 10L);
            verify(redisTemplate, never()).delete(KEY_PREFIX + 10L);
        }

        @Test
        @DisplayName("불일치 + 시도 횟수 한도 미달 → MISMATCH")
        void mismatchUnderLimit_returnsMismatch() {
            when(redisTemplate.execute(any(RedisScript.class), eq(List.of(KEY_PREFIX + 10L)), any(Object[].class)))
                    .thenReturn(0L);
            when(redisTemplate.hasKey(KEY_PREFIX + 10L)).thenReturn(true);
            when(redisTemplate.execute(any(RedisScript.class), eq(List.of(ATTEMPT_PREFIX + 10L)), any(Object[].class)))
                    .thenReturn(3L);

            OneWonVerificationService.VerifyResult result = service.verify(10L, "9999");

            assertThat(result).isEqualTo(OneWonVerificationService.VerifyResult.MISMATCH);
            verify(redisTemplate, never()).delete(KEY_PREFIX + 10L);
        }

        @Test
        @DisplayName("불일치 + 시도 횟수 한도 도달 → LOCKED, 코드/시도 키를 폐기한다")
        void mismatchReachesLimit_returnsLockedAndDeletesKeys() {
            when(redisTemplate.execute(any(RedisScript.class), eq(List.of(KEY_PREFIX + 10L)), any(Object[].class)))
                    .thenReturn(0L);
            when(redisTemplate.hasKey(KEY_PREFIX + 10L)).thenReturn(true);
            when(redisTemplate.execute(any(RedisScript.class), eq(List.of(ATTEMPT_PREFIX + 10L)), any(Object[].class)))
                    .thenReturn(5L);

            OneWonVerificationService.VerifyResult result = service.verify(10L, "9999");

            assertThat(result).isEqualTo(OneWonVerificationService.VerifyResult.LOCKED);
            verify(redisTemplate).delete(KEY_PREFIX + 10L);
            verify(redisTemplate).delete(ATTEMPT_PREFIX + 10L);
        }
    }
}
