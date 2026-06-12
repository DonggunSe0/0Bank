package com.team10.backend.domain.account.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountNicknameUpdateReq (
        @NotBlank(message = "계좌 별칭은 필수 입력 값입니다.")
        @Size(max = 50, message = "계좌 별칭은 50자 이하여야 합니다.")
        String nickname
){
}
