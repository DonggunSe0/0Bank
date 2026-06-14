package com.team10.backend.domain.user.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

/**
 * Redis 기반 1원 송금 인증 코드 관리 서비스.
 *
 * <h2>흐름</h2>
 * <pre>
 * 1. generateAndStore(verificationId, userId) → 하루 요청 한도 체크 → 4자리 코드 생성 → Redis TTL 10분 저장 → 코드 반환
 * 2. verify(verificationId, inputCode) → EXPIRED / MISMATCH / MATCHED / LOCKED 반환
 *    └─ MATCHED 시 Redis 키 즉시 삭제 (재사용 방지)
 * </pre>
 *
 * <h2>Redis 키 전략</h2>
 * <pre>
 * identity:one-won:{verificationId}         → 인증 코드 (TTL 10분)
 * identity:one-won:attempt:{verificationId} → 세션 내 실패 횟수 (TTL 10분)
 * identity:one-won:daily:{userId}           → 하루 송금 요청 횟수 (TTL 24시간)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OneWonVerificationService {

    public enum VerifyResult { MATCHED, MISMATCH, EXPIRED, LOCKED }

    private static final String KEY_PREFIX     = "identity:one-won:";
    private static final String ATTEMPT_PREFIX = "identity:one-won:attempt:";
    private static final String DAILY_PREFIX   = "identity:one-won:daily:";
    private static final Duration TTL          = Duration.ofMinutes(10);
    private static final Duration DAILY_TTL    = Duration.ofDays(1);
    private static final int MAX_ATTEMPTS      = 5;
    private static final int MAX_DAILY         = 10;
    private static final SecureRandom RANDOM   = new SecureRandom();

    /**
     * 최초 생성 시에만 EXPIRE를 설정하는 Lua 스크립트 (daily 카운터용).
     * INCR 결과가 1이면 첫 생성이므로 TTL을 원자적으로 설정한다.
     */
    private static final RedisScript<Long> INCR_WITH_EXPIRE_IF_NEW = RedisScript.of(
            "local v = redis.call('INCR', KEYS[1])\n" +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return v",
            Long.class
    );

    /**
     * INCR + EXPIRE 원자적 실행 Lua 스크립트 (시도 횟수 카운터용).
     * 매번 TTL을 갱신해 슬라이딩 윈도우 효과를 낸다.
     */
    private static final RedisScript<Long> INCR_AND_EXPIRE = RedisScript.of(
            "local v = redis.call('INCR', KEYS[1])\n" +
            "redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "return v",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    /**
     * 하루 요청 한도를 확인하고 4자리 인증코드를 생성하여 Redis에 저장한다 (TTL 10분).
     *
     * <p>userId 기준으로 하루 최대 {@value MAX_DAILY}회 요청 가능.
     * 한도 초과 시 {@link com.team10.backend.domain.user.exception.UserErrorCode#ONE_WON_DAILY_LIMIT_EXCEEDED} 예외를 던진다.
     *
     * @param verificationId 인증 세션 ID
     * @param userId         요청 사용자 ID (하루 한도 카운팅 기준)
     * @return 생성된 4자리 인증 코드
     */
    public String generateAndStore(Long verificationId, Long userId) {
        // 하루 요청 횟수 원자적 증가 후 한도 체크 (Lua: INCR + 첫 생성 시에만 EXPIRE)
        String dailyKey = DAILY_PREFIX + userId;
        Long daily = redisTemplate.execute(
                INCR_WITH_EXPIRE_IF_NEW,
                List.of(dailyKey),
                String.valueOf(DAILY_TTL.toSeconds())
        );
        if (daily > MAX_DAILY) {
            redisTemplate.opsForValue().decrement(dailyKey); // 한도 초과분 되돌리기
            log.warn("[1원 인증] 하루 요청 한도 초과 — userId={}, daily={}", userId, daily);
            throw new com.team10.backend.global.exception.BusinessException(
                    com.team10.backend.domain.user.exception.UserErrorCode.ONE_WON_DAILY_LIMIT_EXCEEDED);
        }

        String code = String.format("%04d", RANDOM.nextInt(10000));
        redisTemplate.opsForValue().set(KEY_PREFIX + verificationId, code, TTL);
        log.info("[1원 인증] 코드 생성 및 저장 — verificationId={}, userId={}, daily={}/{}, TTL=10분",
                verificationId, userId, daily, MAX_DAILY);
        return code;
    }

    /**
     * 사용자가 입력한 코드를 검증한다.
     *
     * <ul>
     *   <li>키 없음 → {@link VerifyResult#EXPIRED}</li>
     *   <li>시도 횟수 초과(5회) → 코드 삭제 후 {@link VerifyResult#LOCKED}</li>
     *   <li>코드 불일치 → 시도 횟수 증가 후 {@link VerifyResult#MISMATCH}</li>
     *   <li>코드 일치 → Redis 키 삭제 후 {@link VerifyResult#MATCHED}</li>
     * </ul>
     */
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
