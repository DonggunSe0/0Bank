package com.team10.backend.domain.user.dto.req;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshReq(
        @NotBlank String accessToken,   // 만료된 AT (userId 추출용)
        @NotBlank String refreshToken   // Opaque RT
) {}
