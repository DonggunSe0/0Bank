package com.team10.backend.domain.user.controller;

import com.team10.backend.domain.user.dto.req.LoginReq;
import com.team10.backend.domain.user.dto.req.TokenRefreshReq;
import com.team10.backend.domain.user.dto.req.UserCreateReq;
import com.team10.backend.domain.user.dto.res.LoginRes;
import com.team10.backend.domain.user.dto.res.TokenRefreshRes;
import com.team10.backend.domain.user.dto.res.UserRes;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.service.UserService;
import com.team10.backend.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    /** HttpOnly 쿠키 이름 — 프론트엔드와 동일하게 맞춰야 함. */
    private static final String RT_COOKIE_NAME = "refresh_token";

    private final UserService userService;

    /**
     * Refresh Token TTL (초 단위). RefreshTokenService 와 동일한 값을 사용한다.
     * application.yml: jwt.refresh-token-expiration-seconds
     */
    @Value("${jwt.refresh-token-expiration-seconds}")
    private long refreshTokenExpirationSeconds;

    /**
     * 쿠키 Secure 플래그 — HTTPS 환경(프로덕션)에서는 true 로 설정.
     * application.yml: cookie.secure (기본값 false, 로컬 개발용)
     */
    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/signup")
    @Operation(summary = "회원가입")
    public ResponseEntity<UserRes> signup(@Valid @RequestBody UserCreateReq request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.signup(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "로그인",
            description = "Access Token(1h)은 응답 바디로, Refresh Token(7일)은 HttpOnly 쿠키로 발급합니다."
    )
    public ResponseEntity<LoginRes> login(@Valid @RequestBody LoginReq request) {
        LoginRes result = userService.login(request);
        ResponseCookie rtCookie = buildRtCookie(result.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(result); // refreshToken 필드는 @JsonIgnore 처리됨
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Access Token 재발급",
            description = "만료된 AT를 바디로, RT는 HttpOnly 쿠키로 전달하면 새 AT + 새 RT를 반환합니다 (Rotation)."
    )
    public ResponseEntity<TokenRefreshRes> refresh(
            @Valid @RequestBody TokenRefreshReq request,
            @CookieValue(name = RT_COOKIE_NAME, required = false) String refreshToken
    ) {
        if (refreshToken == null) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }
        TokenRefreshRes result = userService.refresh(request, refreshToken);
        ResponseCookie rtCookie = buildRtCookie(result.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rtCookie.toString())
                .body(result); // refreshToken 필드는 @JsonIgnore 처리됨
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Redis의 Refresh Token을 삭제하고 RT 쿠키를 만료시킵니다. AT는 블랙리스트에 등록됩니다.")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String accessToken = authHeader.substring(7); // "Bearer " 제거
        userService.logout(userId, accessToken);
        ResponseCookie expiredCookie = expireRtCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    // ──────────────────────────────── 헬퍼 ────────────────────────────────

    /**
     * HttpOnly Refresh Token 쿠키를 생성한다.
     *
     * <ul>
     *   <li>HttpOnly: JS 접근 차단 (XSS 방어)</li>
     *   <li>SameSite=Strict: CSRF 방어</li>
     *   <li>Secure: HTTPS 환경에서만 전송 (cookieSecure 값으로 제어)</li>
     *   <li>Path=/api/v1/auth: 인증 엔드포인트에만 쿠키 전송</li>
     * </ul>
     */
    private ResponseCookie buildRtCookie(String token) {
        return ResponseCookie.from(RT_COOKIE_NAME, token)
                .httpOnly(true)
                .sameSite("Strict")
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(refreshTokenExpirationSeconds))
                .build();
    }

    /** 로그아웃 시 RT 쿠키를 즉시 만료시키는 빈 쿠키를 생성한다. */
    private ResponseCookie expireRtCookie() {
        return ResponseCookie.from(RT_COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Strict")
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}
