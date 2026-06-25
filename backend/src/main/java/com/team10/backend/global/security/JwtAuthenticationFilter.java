package com.team10.backend.global.security;

import com.team10.backend.global.jwt.JwtProvider;
import com.team10.backend.global.jwt.TokenBlocklistService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT Access Token을 검증하고 SecurityContext에 인증 정보를 등록하는 필터.
 *
 * <p>Authorization 헤더에서 Bearer 토큰을 추출하여 서명·만료를 검증한 뒤,
 * {@code UsernamePasswordAuthenticationToken}의 principal에 userId(Long)를 넣어
 * SecurityContext에 저장한다.
 *
 * <p>토큰이 없거나 유효하지 않으면 SecurityContext를 비워 두고 다음 필터로 진행한다.
 * 인증이 필요한 엔드포인트는 {@code SecurityConfig}의 permitAll/authenticated 규칙이 차단한다.
 *
 * <p>로그아웃된 AT는 {@link TokenBlocklistService}의 블랙리스트로 차단한다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final TokenBlocklistService tokenBlocklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            try {
                // 로그아웃 엔드포인트는 만료된 토큰도 허용
                // parseTokenClaims()로 한 번만 파싱해 userId + jti 동시에 추출 (이중 HMAC 검증 방지)
                boolean isLogout = "/api/v1/auth/logout".equals(request.getServletPath());
                JwtProvider.TokenClaims claims = jwtProvider.parseTokenClaims(token, isLogout);
                Long userId = claims.userId();

                // 블랙리스트 체크 — 로그아웃된 AT 차단
                String jti = claims.jti();
                if (tokenBlocklistService.isBlocked(jti)) {
                    log.debug("블랙리스트 AT 요청 차단 — jti={}", jti);
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException e) {
                log.debug("JWT 검증 실패: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Authorization 헤더에서 토큰 문자열을 추출한다. 헤더가 없거나 형식이 맞지 않으면 null. */
    private String resolveToken(HttpServletRequest request) {
        return JwtProvider.resolveBearerToken(request.getHeader("Authorization"));
    }
}
