package com.team10.backend.domain.exchange.dto.req;

import com.team10.backend.domain.exchange.type.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "외화 지갑 생성 요청")
public record FxWalletCreateReq(
        @Schema(description = "생성할 외화 지갑 통화 코드", example = "USD")
        @NotNull(message = "통화는 필수입니다.")
        CurrencyCode currencyCode
) {
}
