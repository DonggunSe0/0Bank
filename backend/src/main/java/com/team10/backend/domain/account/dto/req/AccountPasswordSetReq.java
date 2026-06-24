package com.team10.backend.domain.account.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccountPasswordSetReq(
        @Schema(description = "계좌 비밀번호. 숫자 6자리", example = "123456")
        @NotBlank(message = "계좌 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "계좌 비밀번호는 숫자 6자리여야 합니다.")
        String accountPassword
) {
}