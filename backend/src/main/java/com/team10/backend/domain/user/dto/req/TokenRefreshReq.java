package com.team10.backend.domain.user.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Access Token 재발급 요청.
 * Refresh Token은 HttpOnly 쿠키로 전달되므로 요청 바디에 포함하지 않는다.
 */
public record TokenRefreshReq(
        @NotBlank String accessToken    // 만료된 AT (userId 추출용)
) {}
