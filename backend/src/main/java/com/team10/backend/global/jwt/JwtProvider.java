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
import java.util.UUID;

/**
 * JWT Access Token 생성 및 파싱 컴포넌트.
 *
 * <h2>클레임 구조</h2>
 * <pre>
 * {
 *   "sub":   "1",           // userId (String)
 *   "email": "a@b.com",
 *   "jti":   "uuid-v4",    // 토큰 고유 ID (블랙리스트용)
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
                .id(UUID.randomUUID().toString())   // jti — 블랙리스트 식별자
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

    /**
     * userId와 jti를 한 번의 파싱으로 함께 반환한다.
     *
     * <p>JWT 파싱은 서명 검증 + base64 디코딩을 수반하므로,
     * userId와 jti가 모두 필요한 경우 두 번 파싱하는 대신 이 메서드를 사용한다.
     *
     * @param ignoreExpiry true이면 만료된 토큰도 허용 (서명만 검증)
     * @throws JwtException 서명 불일치 또는 ignoreExpiry=false이고 만료 시
     */
    public TokenClaims parseTokenClaims(String token, boolean ignoreExpiry) {
        Claims claims = parseClaims(token, ignoreExpiry);
        return new TokenClaims(Long.parseLong(claims.getSubject()), claims.getId());
    }

    /** userId와 jti를 담는 파싱 결과 레코드. */
    public record TokenClaims(Long userId, String jti) {}

    /**
     * 토큰에서 jti(JWT ID)를 추출한다. 만료된 토큰도 허용한다.
     *
     * @throws JwtException 서명 불일치 시
     */
    public String extractJti(String token) {
        return parseClaims(token, true).getId();
    }

    /**
     * 토큰의 잔여 유효 시간(초)을 반환한다. 이미 만료됐으면 0을 반환한다.
     *
     * @throws JwtException 서명 불일치 시
     */
    public long getRemainingExpirySeconds(String token) {
        Date expiry = parseClaims(token, true).getExpiration();
        long remaining = (expiry.getTime() - System.currentTimeMillis()) / 1000L;
        return Math.max(remaining, 0L);
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
