package com.team10.backend.domain.user.dto.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginReq(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
