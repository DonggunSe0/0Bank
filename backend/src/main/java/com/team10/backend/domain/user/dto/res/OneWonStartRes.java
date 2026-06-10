package com.team10.backend.domain.user.dto.res;

import com.team10.backend.domain.user.type.VerificationStatus;

public record OneWonStartRes(
        Long verificationId,
        VerificationStatus status,
        String message
) {}
