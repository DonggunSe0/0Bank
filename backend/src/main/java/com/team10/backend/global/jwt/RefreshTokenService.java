package com.team10.backend.global.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
     * Refresh Token이 Redis의 값과 일치하는지 검증한다.
     *
     * @param userId 사용자 PK
     * @param token  클라이언트가 제출한 Refresh Token
     * @return 일치 여부 (만료됐거나 불일치 시 false)
     */
    public boolean validate(Long userId, String token) {
        String stored = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        return token != null && token.equals(stored);
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
