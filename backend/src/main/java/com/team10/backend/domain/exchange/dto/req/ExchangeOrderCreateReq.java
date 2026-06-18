package com.team10.backend.domain.exchange.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "환전 주문 실행 요청")
public record ExchangeOrderCreateReq(
        @Schema(description = "환전 견적 ID", example = "1")
        @NotNull(message = "환전 견적 ID는 필수입니다.")
        Long exchangeQuoteId,

        @Schema(description = "원화 출금 또는 입금 계좌 ID", example = "1")
        @NotNull(message = "원화 계좌 ID는 필수입니다.")
        Long krwAccountId,

        @Schema(description = "외화 지갑 ID", example = "1")
        @NotNull(message = "외화 지갑 ID는 필수입니다.")
        Long fxWalletId,

        @Schema(description = "환전 주문 멱등성 키", example = "exchange-order-20260617-0001")
        @NotBlank(message = "멱등성 키는 필수입니다.")
        String idempotencyKey
) {
}
