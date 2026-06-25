package com.team10.backend.domain.user.dto.req;

import jakarta.validation.constraints.NotNull;

public record ConsentUpdateReq(

        @NotNull(message = "마케팅 수신 동의 여부는 필수입니다.")
        Boolean marketingAgreed
) {
}
