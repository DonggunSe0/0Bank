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
import com.team10.backend.global.exception.GlobalErrorCode;
import com.team10.backend.global.jwt.JwtProvider;
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

    // 프론트엔드와 동일해야 함
    private static final String RT_COOKIE_NAME = "refresh_token";

    private final UserService userService;

    @Value("${jwt.refresh-token-expiration-seconds}")
    private long refreshTokenExpirationSeconds;

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
                .body(result);
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
                .body(result);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Redis의 Refresh Token을 삭제하고 RT 쿠키를 만료시킵니다. AT는 블랙리스트에 등록됩니다.")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String accessToken = JwtProvider.resolveBearerToken(authHeader);
        if (accessToken == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        userService.logout(userId, accessToken);
        ResponseCookie expiredCookie = expireRtCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    /** HttpOnly + SameSite=Strict RT 쿠키 생성 (XSS/CSRF 방어) */
    private ResponseCookie buildRtCookie(String token) {
        return ResponseCookie.from(RT_COOKIE_NAME, token)
                .httpOnly(true)
                .sameSite("Strict")
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(Duration.ofSeconds(refreshTokenExpirationSeconds))
                .build();
    }

    /** maxAge=0 으로 RT 쿠키 즉시 만료 */
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
