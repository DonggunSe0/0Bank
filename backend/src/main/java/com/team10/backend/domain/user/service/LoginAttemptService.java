package com.team10.backend.domain.user.service;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 로그인 실패 횟수를 Redis로 관리하는 서비스.
 *
 * <h2>Redis 키 전략</h2>
 * <pre>
 * login:fail:{email} → 실패 횟수 (TTL 30분, 실패할 때마다 갱신)
 * </pre>
 *
 * <h2>잠금 정책</h2>
 * <ul>
 *   <li>5회 이상 실패 시 30분간 로그인 차단</li>
 *   <li>TTL은 마지막 실패 시점 기준으로 연장됨 (슬라이딩 윈도우)</li>
 *   <li>로그인 성공 시 카운터 즉시 초기화</li>
 *   <li>이메일 발송 없이 Redis TTL 만료로 자동 해제</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final int MAX_FAIL_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    /**
     * 현재 잠금 상태인지 확인하고, 잠금 중이면 예외를 던진다.
     *
     * @param email 로그인 시도 이메일
     * @throws BusinessException {@link UserErrorCode#LOGIN_LOCKED} — 5회 이상 실패 상태
     */
    public void checkAndThrowIfLocked(String email) {
        String value = redisTemplate.opsForValue().get(FAIL_KEY_PREFIX + email);
        if (value != null && Long.parseLong(value) >= MAX_FAIL_COUNT) {
            log.warn("[LoginAttempt] 잠금 상태 — email={}", email);
            throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
        }
    }

    /**
     * 로그인 실패를 기록한다. 5회 이상이면 이후 요청은 {@link #checkAndThrowIfLocked}에서 차단된다.
     *
     * <p>TTL은 마지막 실패 시점 기준 30분으로 갱신된다 (슬라이딩 윈도우).
     *
     * @param email 로그인 시도 이메일
     */
    public void recordFailure(String email) {
        String key = FAIL_KEY_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, LOCK_DURATION);
        log.warn("[LoginAttempt] 실패 기록 — email={}, failCount={}", email, count);
    }

    /**
     * 로그인 성공 시 실패 카운터를 초기화한다.
     *
     * @param email 로그인 성공 이메일
     */
    public void clearFailures(String email) {
        redisTemplate.delete(FAIL_KEY_PREFIX + email);
        log.info("[LoginAttempt] 카운터 초기화 — email={}", email);
    }
}
