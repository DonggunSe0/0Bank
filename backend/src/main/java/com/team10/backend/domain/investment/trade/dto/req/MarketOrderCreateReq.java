package com.team10.backend.domain.investment.trade.dto.req;

import com.team10.backend.domain.investment.trade.type.InvestmentTradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@Schema(description = "주식 시장가 즉시 체결 주문 요청")
public record MarketOrderCreateReq(
        @Schema(description = "투자 계좌 ID", example = "1")
        @NotNull(message = "투자 계좌 ID는 필수입니다.")
        Long accountId,

        @Schema(description = "종목 ID", example = "1")
        @NotNull(message = "종목 ID는 필수입니다.")
        Long stockId,

        @Schema(description = "실시간 호가 SSE stream ID", example = "8e14f3b2-6f3a-4f30-9f59-cf13f9337f15")
        @NotBlank(message = "실시간 호가 stream ID는 필수입니다.")
        String streamId,

        @Schema(description = "매매 구분", allowableValues = {"BUY", "SELL"})
        @NotNull(message = "매매 구분은 필수입니다.")
        InvestmentTradeType tradeType,

        @Schema(description = "주문 수량", example = "10")
        @NotNull(message = "주문 수량은 필수입니다.")
        @Positive(message = "주문 수량은 양수여야 합니다.")
        Long quantity,

        @Schema(description = "투자 계좌 비밀번호. 숫자 6자리", example = "123456")
        @NotBlank(message = "투자 계좌 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "투자 계좌 비밀번호는 숫자 6자리여야 합니다.")
        String accountPassword,

        @Schema(description = "사용자가 주문 시 기대한 가격", example = "72000")
        @NotNull(message = "기대 가격은 필수입니다.")
        @Positive(message = "기대 가격은 양수여야 합니다.")
        Long expectedPrice
) {
}
