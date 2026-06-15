package com.team10.backend.global.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Opaque Refresh Token Redis 관리 서비스.
 *
 * <h2>Redis 키 전략</h2>
 * <pre>
 * refresh:{userId} → UUID 문자열 (TTL 7일)
 * </pre>
 *
 * <h2>Refresh Token 검증 흐름</h2>
 * <ol>
 *   <li>클라이언트가 만료된 Access Token + Refresh Token 전송</li>
 *   <li>{@link JwtProvider#parseUserIdIgnoreExpiry}로 만료된 AT에서 userId 추출</li>
 *   <li>{@link #validate}로 Redis의 토큰과 비교</li>
 *   <li>일치하면 새 AT + 새 RT 발급 (Refresh Token Rotation)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    /**
     * 값이 일치할 때만 원자적으로 삭제하는 Lua 스크립트.
     * 불일치 시 키를 삭제하지 않으므로 잘못된 RT 제출이 올바른 RT를 무효화하지 않는다.
     * 반환값: 1 = 일치 후 삭제, 0 = 불일치 또는 키 없음
     */
    private static final RedisScript<Long> GET_AND_DELETE_IF_MATCH = RedisScript.of(
            "local stored = redis.call('GET', KEYS[1])\n" +
            "if stored == ARGV[1] then\n" +
            "  redis.call('DEL', KEYS[1])\n" +
            "  return 1\n" +
            "else\n" +
            "  return 0\n" +
            "end",
            Long.class
    );

    @Value("${jwt.refresh-token-expiration-seconds}")
    private long refreshTokenExpirationSeconds;

    private final StringRedisTemplate redisTemplate;

    /**
     * 새 Refresh Token을 발급하고 Redis에 저장한다 (TTL 7일).
     * 기존 토큰이 있으면 덮어써서 단일 세션을 보장한다.
     *
     * @param userId 사용자 PK
     * @return 발급된 Opaque Refresh Token
     */
    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                KEY_PREFIX + userId,
                token,
                Duration.ofSeconds(refreshTokenExpirationSeconds)
        );
        log.info("[RefreshToken] 발급 — userId={}", userId);
        return token;
    }

    /**
     * Refresh Token Rotation 전용 원자적 검증.
     *
     * <p>Lua 스크립트로 GET + 일치 시에만 DEL을 원자적으로 실행한다.
     * 동시에 두 요청이 같은 RT로 재발급을 시도해도 하나만 성공한다 (race condition 방지).
     * 불일치 토큰 제출 시 Redis의 올바른 RT는 보존된다.
     *
     * @param userId 사용자 PK
     * @param token  클라이언트가 제출한 Refresh Token
     * @return 일치 여부 (만료됐거나 불일치 시 false)
     */
    public boolean validateAndConsume(Long userId, String token) {
        if (token == null) return false;
        Long result = redisTemplate.execute(
                GET_AND_DELETE_IF_MATCH,
                List.of(KEY_PREFIX + userId),
                token
        );
        return Long.valueOf(1L).equals(result);
    }

    /**
     * Refresh Token을 삭제한다 (로그아웃 / 강제 만료).
     *
     * @param userId 사용자 PK
     */
    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
        log.info("[RefreshToken] 삭제 — userId={}", userId);
    }
}
