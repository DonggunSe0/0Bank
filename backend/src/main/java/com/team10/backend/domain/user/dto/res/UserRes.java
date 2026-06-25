package com.team10.backend.domain.user.dto.res;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserRes(
        Long id,
        String email,
        String name,
        String phoneNumber,
        LocalDate birthDate,
        Boolean identityVerified,
        LocalDateTime createdAt
) {
}
