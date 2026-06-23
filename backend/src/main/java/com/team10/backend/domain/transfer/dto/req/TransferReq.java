package com.team10.backend.domain.transfer.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "계좌 간 송금 요청")
public record TransferReq(
        @Schema(description = "출금 계좌 ID", example = "1")
        @NotNull
        Long senderAccountId,

        @Schema(description = "입금 계좌번호", example = "100200300002")
        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[0-9-]+$") // 숫자(0~9)와 하이픈(-)으로만 이루어진 문자열
        String receiverAccountNumber,

        @Schema(description = "송금 금액", example = "50000")
        @NotNull
        @Positive
        Long amount,

        @Schema(description = "송금 메모", example = "점심값")
        @Size(max = 100)
        String memo
) {
}
