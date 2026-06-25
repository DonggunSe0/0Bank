package com.team10.backend.global.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 로그아웃된 Access Token을 블랙리스트로 관리하는 서비스.
 *
 * <h2>Redis 키 전략</h2>
 * <pre>
 * blocklist:{jti} → "1"  (TTL = AT 잔여 만료 시간)
 * </pre>
 *
 * <h2>동작 원리</h2>
 * <ol>
 *   <li>로그아웃 시 AT의 jti를 Redis에 저장 (TTL = 잔여 만료시간)</li>
 *   <li>JwtAuthenticationFilter가 매 요청마다 jti 블랙리스트 여부 확인</li>
 *   <li>AT 만료 후에는 TTL로 Redis 키가 자동 삭제됨 (저장소 낭비 없음)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlocklistService {

    private static final String BLOCKLIST_PREFIX = "blocklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Access Token을 블랙리스트에 등록한다.
     *
     * @param jti              AT의 jti 클레임
     * @param remainingSeconds AT의 잔여 유효 시간(초) — 이 시간 후 자동 삭제
     */
    public void block(String jti, long remainingSeconds) {
        if (remainingSeconds <= 0) return; // 이미 만료된 토큰은 저장 불필요
        redisTemplate.opsForValue().set(
                BLOCKLIST_PREFIX + jti,
                "1",
                Duration.ofSeconds(remainingSeconds)
        );
        log.info("[Blocklist] AT 차단 등록 — jti={}, ttl={}s", jti, remainingSeconds);
    }

    /**
     * jti가 블랙리스트에 등록되어 있는지 확인한다.
     *
     * @param jti AT의 jti 클레임
     * @return 블랙리스트 등록 여부
     */
    public boolean isBlocked(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLOCKLIST_PREFIX + jti));
    }
}
