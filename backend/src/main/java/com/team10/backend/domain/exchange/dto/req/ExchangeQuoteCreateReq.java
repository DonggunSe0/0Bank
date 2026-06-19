package com.team10.backend.domain.exchange.dto.req;

import com.team10.backend.domain.exchange.type.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "환전 견적 생성 요청")
public record ExchangeQuoteCreateReq(
        @Schema(description = "출금 통화 코드", example = "KRW")
        @NotNull(message = "출금 통화는 필수입니다.")
        CurrencyCode fromCurrencyCode,

        @Schema(description = "입금 통화 코드", example = "USD")
        @NotNull(message = "입금 통화는 필수입니다.")
        CurrencyCode toCurrencyCode,

        @Schema(description = "환전 요청 금액", example = "100000")
        @NotNull(message = "환전 금액은 필수입니다.")
        @Positive(message = "환전 금액은 0보다 커야 합니다.")
        BigDecimal fromAmount
) {
}
