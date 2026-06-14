package com.team10.backend.domain.user.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OneWonStartReq(
        @NotBlank
        @Pattern(regexp = "^\\d{10,14}$", message = "계좌번호는 10~14자리 숫자여야 합니다.")
        String accountNumber,

        /** CODEF 은행 기관코드 (예: 004=국민, 011=농협, 020=우리, 081=하나, 088=신한) */
        @NotBlank(message = "은행 기관코드는 필수입니다.")
        @Pattern(regexp = "^\\d{3}$", message = "기관코드는 3자리 숫자여야 합니다.")
        String organization
) {}
