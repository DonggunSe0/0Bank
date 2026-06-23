package com.team10.backend.domain.user.verification;

import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.util.DailyResetClock;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

/** Redis 기반 1원 송금 인증 코드 관리 — 하루 10회 한도(매일 00:00 KST 리셋), 5회 실패 시 잠금 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OneWonVerificationService {

    public enum VerifyResult { MATCHED, MISMATCH, EXPIRED, LOCKED }

    private static final String KEY_PREFIX     = "identity:one-won:";
    private static final String ATTEMPT_PREFIX = "identity:one-won:attempt:";
    private static final String DAILY_PREFIX   = "identity:one-won:daily:";
    private static final String LOCK_PREFIX    = "identity:one-won:lock:";
    private static final Duration TTL          = Duration.ofMinutes(10);
    private static final Duration LOCK_TTL     = Duration.ofSeconds(35);
    private static final int MAX_ATTEMPTS      = 5;
    private static final int MAX_DAILY         = 10;
    private static final SecureRandom RANDOM   = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    // RedisScriptConfig에서 공용으로 정의 — IdentityVerificationService의 OCR daily 카운터와 동일한 스크립트
    private final RedisScript<Long> incrWithExpireIfNewScript;
    // RedisScriptConfig에서 공용으로 정의 — LoginAttemptService의 실패 카운터와 동일한 스크립트
    private final RedisScript<Long> incrAndExpireScript;

    /** 1원 인증 시작 동시 요청 방지 락(SET NX). */
    public boolean tryAcquireStartLock(Long userId) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + userId, "1", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    /** 1원 인증 시작 처리(성공/실패 불문) 완료 후 락 해제 */
    public void releaseStartLock(Long userId) {
        redisTemplate.delete(LOCK_PREFIX + userId);
    }

    public String generateAndStore(Long verificationId, Long userId) {
        String dailyKey = DAILY_PREFIX + userId;
        // 매번 호출 시점부터 다음 자정(KST)까지 남은 초를 TTL로 넘긴다 — 키가 그날 처음 만들어질 때만
        // EXPIRE가 적용되므로(incrWithExpireIfNewScript), 결과적으로 모든 사용자가 자정에 리셋된다.
        Long daily = redisTemplate.execute(
                incrWithExpireIfNewScript,
                List.of(dailyKey),
                String.valueOf(DailyResetClock.secondsUntilNextMidnight())
        );
        if (daily == null) {
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (daily > MAX_DAILY) {
            log.warn("[1원 인증] 하루 요청 한도 초과 — userId={}, daily={}", userId, daily);
            throw new BusinessException(UserErrorCode.ONE_WON_DAILY_LIMIT_EXCEEDED);
        }

        String code = String.format("%04d", RANDOM.nextInt(10000));
        redisTemplate.opsForValue().set(KEY_PREFIX + verificationId, code, TTL);
        log.info("[1원 인증] 코드 생성 및 저장 — verificationId={}, userId={}, code={}, daily={}/{}, TTL=10분",
                verificationId, userId, code, daily, MAX_DAILY);
        return code;
    }

    /** 송금 실패 보상 — Redis 인증 코드 삭제 */
    public void deleteCode(Long verificationId) {
        redisTemplate.delete(KEY_PREFIX + verificationId);
        log.warn("[1원 인증] 송금 실패로 코드 삭제 — verificationId={}", verificationId);
    }

    /** 송금 실패 보상 — generateAndStore에서 먼저 증가한 일일 카운터 감소 */
    public void decrementDailyCount(Long userId) {
        String dailyKey = DAILY_PREFIX + userId;
        redisTemplate.opsForValue().decrement(dailyKey);
        log.warn("[1원 인증] 송금 실패로 일일 카운터 감소 — userId={}", userId);
    }

    public VerifyResult verify(Long verificationId, String inputCode) {
        String key = KEY_PREFIX + verificationId;
        String attemptKey = ATTEMPT_PREFIX + verificationId;
        String stored = redisTemplate.opsForValue().get(key);

        if (stored == null) {
            log.warn("[1원 인증] 코드 만료 또는 없음 — verificationId={}", verificationId);
            return VerifyResult.EXPIRED;
        }

        if (!stored.equals(inputCode)) {
            Long attempts = redisTemplate.execute(
                    incrAndExpireScript,
                    List.of(attemptKey),
                    String.valueOf(TTL.toSeconds())
            );
            log.warn("[1원 인증] 코드 불일치 — verificationId={}, attempts={}", verificationId, attempts);

            if (attempts != null && attempts >= MAX_ATTEMPTS) {
                redisTemplate.delete(key);
                redisTemplate.delete(attemptKey);
                log.warn("[1원 인증] 시도 횟수 초과 — verificationId={}, 코드 폐기", verificationId);
                return VerifyResult.LOCKED;
            }
            return VerifyResult.MISMATCH;
        }

        redisTemplate.delete(key);
        redisTemplate.delete(attemptKey);
        log.info("[1원 인증] 코드 일치 — verificationId={}", verificationId);
        return VerifyResult.MATCHED;
    }
}
