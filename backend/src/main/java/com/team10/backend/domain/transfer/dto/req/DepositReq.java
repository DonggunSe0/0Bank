package com.team10.backend.domain.transfer.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "입금 요청")
public record DepositReq(
        @Schema(description = "입금 대상 계좌 ID", example = "1")
        @NotNull
        Long accountId,

        @Schema(description = "입금 금액", example = "100000")
        @NotNull
        @Positive
        Long amount,

        @Schema(description = "입금 메모", example = "초기 입금")
        @Size(max = 100)
        String memo
) {
}
