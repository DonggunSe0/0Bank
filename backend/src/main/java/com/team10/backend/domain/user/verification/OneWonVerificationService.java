package com.team10.backend.domain.user.verification;

import com.team10.backend.domain.user.exception.UserErrorCode;
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

/** Redis 기반 1원 송금 인증 코드 관리 — 하루 10회 한도, 5회 실패 시 잠금 */
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
    private static final Duration DAILY_TTL    = Duration.ofDays(1);
    private static final Duration LOCK_TTL     = Duration.ofSeconds(35);
    private static final int MAX_ATTEMPTS      = 5;
    private static final int MAX_DAILY         = 10;
    private static final SecureRandom RANDOM   = new SecureRandom();

    // 첫 INCR 시에만 EXPIRE — 이후엔 TTL 덮어쓰지 않음 (daily 카운터용)
    private static final RedisScript<Long> INCR_WITH_EXPIRE_IF_NEW = RedisScript.of(
            "local v = redis.call('INCR', KEYS[1])\n" +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return v",
            Long.class
    );

    // INCR + EXPIRE 원자적 실행 — 매번 TTL 갱신 (슬라이딩 윈도우, 시도 횟수 카운터용)
    private static final RedisScript<Long> INCR_AND_EXPIRE = RedisScript.of(
            "local v = redis.call('INCR', KEYS[1])\n" +
            "redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "return v",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    /**
     * 1원 인증 시작 동시 요청 방지용 락 획득.
     * 같은 유저가 거의 동시에 두 번 호출하면 실제 송금(sendOneWon)이 중복 실행될 수 있어
     * userId 기준으로 짧은 TTL의 락을 건다 (SET NX 방식, 원자적).
     *
     * @return 락 획득 성공 시 true, 이미 처리 중이면 false
     */
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
        Long daily = redisTemplate.execute(
                INCR_WITH_EXPIRE_IF_NEW,
                List.of(dailyKey),
                String.valueOf(DAILY_TTL.toSeconds())
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
                    INCR_AND_EXPIRE,
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
