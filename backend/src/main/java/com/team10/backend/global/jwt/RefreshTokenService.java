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

/** Opaque Refresh Token Redis 관리 서비스. {@code refresh:{userId}} → UUID(TTL 7일), 검증 일치 시 AT+RT를 함께 재발급한다(Rotation). */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    @Value("${jwt.refresh-token-expiration-seconds}")
    private long refreshTokenExpirationSeconds;

    private final StringRedisTemplate redisTemplate;
    // RedisScriptConfig 정의 — 일치 시에만 원자적 삭제(1=삭제, 0=불일치/없음), 불일치 시 기존 RT는 보존된다.
    private final RedisScript<Long> getAndDeleteIfMatchScript;

    /** 새 Refresh Token을 발급해 Redis에 저장한다(TTL 7일) — 기존 토큰은 덮어써 단일 세션을 보장한다. */
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

    /** Rotation 전용 원자적 검증 — Lua 스크립트로 GET+일치 시 DEL을 원자 실행해 동시 요청 중 하나만 성공시킨다. */
    public boolean validateAndConsume(Long userId, String token) {
        if (token == null) return false;
        Long result = redisTemplate.execute(
                getAndDeleteIfMatchScript,
                List.of(KEY_PREFIX + userId),
                token
        );
        return Long.valueOf(1L).equals(result);
    }

    /** Refresh Token을 삭제한다 (로그아웃 / 강제 만료). */
    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
        log.info("[RefreshToken] 삭제 — userId={}", userId);
    }
}
