package com.team10.backend.global.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT Access Token 생성 및 파싱 컴포넌트.
 *
 * <h2>클레임 구조</h2>
 * <pre>
 * {
 *   "sub":   "1",           // userId (String)
 *   "email": "a@b.com",
 *   "iat":   ...,
 *   "exp":   ...            // iat + 1h
 * }
 * </pre>
 *
 * <h2>Refresh Token 연계 전략</h2>
 * <p>만료된 Access Token도 서명만 유효하면 {@link #parseUserIdIgnoreExpiry}로 userId를 추출할 수 있다.
 * 이를 이용해 Redis의 {@code refresh:{userId}} 키와 대조하여 Refresh Token을 검증한다.
 */
@Slf4j
@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration-seconds}")
    private long accessTokenExpirationSeconds;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // secret이 Base64 인코딩 문자열이 아닌 일반 문자열이면 UTF-8 바이트로 직접 키 생성
        byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Access Token을 생성한다.
     *
     * @param userId 사용자 PK
     * @param email  사용자 이메일
     * @return 서명된 JWT 문자열
     */
    public String createAccessToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationSeconds * 1000L);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Access Token에서 userId를 추출한다 (만료 검증 포함).
     *
     * @throws JwtException 서명 불일치 또는 만료 시
     */
    public Long parseUserId(String token) {
        return Long.parseLong(
                parseClaims(token, false).getSubject()
        );
    }

    /**
     * 만료된 Access Token에서도 userId를 추출한다 (서명만 검증).
     *
     * <p>Refresh Token 검증 시 만료된 Access Token에서 userId를 읽어야 할 때 사용한다.
     *
     * @throws JwtException 서명 불일치 시
     */
    public Long parseUserIdIgnoreExpiry(String token) {
        return Long.parseLong(
                parseClaims(token, true).getSubject()
        );
    }

    private Claims parseClaims(String token, boolean ignoreExpiry) {
        JwtParser parser = Jwts.parser().verifyWith(key).build();
        try {
            return parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            if (ignoreExpiry) {
                return e.getClaims(); // 만료된 토큰의 클레임 반환
            }
            throw e;
        }
    }
}
