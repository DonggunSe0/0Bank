package com.team10.backend.domain.user.verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Redis 기반 1원 송금 인증 코드 관리 서비스.
 *
 * <h2>흐름</h2>
 * <pre>
 * 1. generateAndStore(verificationId) → 4자리 코드 생성 → Redis TTL 10분 저장 → 코드 반환
 * 2. verify(verificationId, inputCode) → EXPIRED / MISMATCH / MATCHED 반환
 *    └─ MATCHED 시 Redis 키 즉시 삭제 (재사용 방지)
 * </pre>
 *
 * <p>Redis 키: {@code identity:one-won:{verificationId}}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OneWonVerificationService {

    public enum VerifyResult { MATCHED, MISMATCH, EXPIRED, LOCKED }

    private static final String KEY_PREFIX = "identity:one-won:";
    private static final String ATTEMPT_PREFIX = "identity:one-won:attempt:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    /**
     * 4자리 인증코드를 생성하고 Redis에 저장한다 (TTL 10분).
     */
    public String generateAndStore(Long verificationId) {
        String code = String.format("%04d", RANDOM.nextInt(10000));
        redisTemplate.opsForValue().set(KEY_PREFIX + verificationId, code, TTL);
        log.info("[1원 인증] 코드 생성 및 저장 — verificationId={}, code={}, TTL=10분", verificationId, code);
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
            Long attempts = redisTemplate.opsForValue().increment(attemptKey);
            redisTemplate.expire(attemptKey, TTL);
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
