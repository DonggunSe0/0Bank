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
    private static final long LOCKED_SENTINEL = -1L;

    private final StringRedisTemplate redisTemplate;
    // RedisScriptConfig에서 공용으로 정의 — 임계값 미만일 때만 원자적으로 INCR+EXPIRE하고, 이미
    // 임계값 이상이면 증가 없이 -1을 반환한다. 과거엔 "확인(GET)"과 "기록(INCR)"이 별도 호출로 분리돼
    // 있어, 동시에 도착한 요청들이 모두 "아직 안전"으로 읽은 뒤 같이 BCrypt까지 통과하는 TOCTOU race가
    // 있었다 — 이 스크립트로 확인+예약을 하나의 원자 연산으로 묶어서 막는다.
    private final RedisScript<Long> checkAndIncrIfBelowLimitScript;

    /**
     * 이번 로그인 시도를 위한 실패 슬롯을 원자적으로 예약한다.
     * 이미 누적 실패가 MAX_FAIL_COUNT 이상이면 증가시키지 않고 즉시 LOGIN_LOCKED.
     * 슬롯이 예약되면(=이 메서드가 정상 반환되면) BCrypt 비교를 진행해도 된다는 뜻이고,
     * 그 예약 자체가 실패 기록이므로 호출 측에서 별도로 실패를 기록할 필요는 없다 —
     * 로그인에 성공한 경우에만 clearFailures()로 되돌리면 된다.
     */
    public void checkAndRecordAttempt(String email) {
        String key = FAIL_KEY_PREFIX + email;
        Long result = redisTemplate.execute(
                checkAndIncrIfBelowLimitScript,
                List.of(key),
                String.valueOf(MAX_FAIL_COUNT),
                String.valueOf(LOCK_DURATION.toSeconds())
        );
        if (result == null || result == LOCKED_SENTINEL) {
            log.warn("[LoginAttempt] 잠금 상태 — email={}", email);
            throw new BusinessException(UserErrorCode.LOGIN_LOCKED);
        }
    }

    public void clearFailures(String email) {
        redisTemplate.delete(FAIL_KEY_PREFIX + email);
        log.info("[LoginAttempt] 카운터 초기화 — email={}", email);
    }
}
