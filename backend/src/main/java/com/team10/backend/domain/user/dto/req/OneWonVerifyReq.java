package com.team10.backend.domain.user.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OneWonVerifyReq(
        @NotBlank
        @Pattern(regexp = "^\\d{4}$", message = "인증코드는 4자리 숫자여야 합니다.")
        String code
) {}
