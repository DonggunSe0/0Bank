package com.team10.backend.domain.account.dto.req;

import com.team10.backend.domain.account.type.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountCreateReq(
        @Schema(description = "계좌 별칭", example = "생활비 계좌")
        @Size(max = 50, message = "계좌 별칭은 50자 이하여야 합니다.")
        String nickname,

        @Schema(description = "계좌 타입", example = "DEPOSIT")
        @NotNull(message="계좌 타입은 필수입니다.")
        AccountType accountType,

        @Schema(description = "계좌 비밀번호. 숫자 6자리", example = "123456")
        @NotBlank(message = "계좌 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "계좌 비밀번호는 숫자 6자리여야 합니다.")
        String accountPassword
){
}
