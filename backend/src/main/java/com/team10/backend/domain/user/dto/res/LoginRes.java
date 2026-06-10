package com.team10.backend.domain.user.dto.res;

public record LoginRes(
        String accessToken,
        String refreshToken,
        UserRes user
) {}
