package com.team10.backend.domain.exAccount.dto.res;

import com.team10.backend.domain.exAccount.type.ExAccountConnectionStatus;

public record ExAccountConnectionRes(
        String organization,
        ExAccountConnectionStatus status
) {
}
