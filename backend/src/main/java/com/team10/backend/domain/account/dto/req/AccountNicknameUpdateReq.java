package com.team10.backend.domain.account.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountNicknameUpdateReq (
        @Schema(description = "변경할 계좌 별칭", example = "급여 계좌")
        @NotBlank(message = "계좌 별칭은 필수 입력 값입니다.")
        @Size(max = 50, message = "계좌 별칭은 50자 이하여야 합니다.")
        String nickname
){
}
