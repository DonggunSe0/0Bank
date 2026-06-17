package com.team10.backend.domain.user.service;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/** 로그인 실패 횟수 Redis 관리 — 5회 실패 시 30분 잠금 (슬라이딩 윈도우) */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final int MAX_FAIL_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    // INCR + EXPIRE 원자적 실행 — 중간에 서버가 죽어도 TTL 보장
    private static final RedisScript<Long> INCR_AND_EXPIRE = RedisScript.of(
            "local v = redis.call('INCR', KEYS[1])\n" +
            "redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "return v",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public void checkAndThrowIfLocked(String email) {
        String value = redisTemplate.opsForValue().get(FAIL_KEY_PREFIX + email);
        if (value != null && Long.parseLong(value) >= MAX_FAIL_COUNT) {
            log.warn("[LoginAttempt] 잠금 상태 — email={}", email);
            throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
        }
    }

    public void recordFailure(String email) {
        String key = FAIL_KEY_PREFIX + email;
        Long count = redisTemplate.execute(
                INCR_AND_EXPIRE,
                List.of(key),
                String.valueOf(LOCK_DURATION.toSeconds())
        );
        log.warn("[LoginAttempt] 실패 기록 — email={}, failCount={}", email, count);
    }

    public void clearFailures(String email) {
        redisTemplate.delete(FAIL_KEY_PREFIX + email);
        log.info("[LoginAttempt] 카운터 초기화 — email={}", email);
    }
}
