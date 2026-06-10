package com.team10.backend.domain.user.dto.res;

public record TokenRefreshRes(
        String accessToken,
        String refreshToken
) {}
