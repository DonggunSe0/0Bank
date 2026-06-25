package com.team10.backend.domain.investment.trade.dto.res;

import static com.team10.backend.domain.investment.config.KisConstants.SEOUL_ZONE;

import com.team10.backend.domain.investment.trade.entity.InvestmentTrade;
import com.team10.backend.domain.investment.trade.type.InvestmentTradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "주식 시장가 체결 응답")

public record InvestmentTradeRes(

        @Schema(description = "거래 ID", example = "1")
        Long id,

        @Schema(description = "투자 계좌 ID", example = "1")
        Long accountId,

        @Schema(description = "종목 ID", example = "1")
        Long stockId,

        @Schema(description = "종목 코드", example = "005930")
        String stockCode,

        @Schema(description = "종목명", example = "삼성전자")
        String stockName,

        @Schema(description = "매매 구분", allowableValues = {"BUY", "SELL"})
        InvestmentTradeType tradeType,

        @Schema(description = "체결 수량", example = "10")
        Long quantity,

        @Schema(description = "실제 체결 가격", example = "72000")
        Long executionPrice,

        @Schema(description = "총 체결 금액 (체결가 × 수량)", example = "720000")
        Long totalAmount,

        @Schema(description = "사용자가 주문 시 기대한 가격", example = "71800")
        Long requestedPrice,

        @Schema(description = "기대 가격 대비 실제 체결 가격 편차 (bps 단위, 100bps = 1%)", example = "27")
        Integer priceDeviationBps,

        @Schema(description = "체결 가격 산정에 사용된 실시간 호가 스냅샷 수신 시각", example = "2026-06-23T14:30:01")
        LocalDateTime snapshotAt,

        @Schema(description = "거래 체결 시각", example = "2026-06-23T14:30:02")
        LocalDateTime executedAt

) {
    public static InvestmentTradeRes from(InvestmentTrade trade) {
        return new InvestmentTradeRes(
                trade.getId(),
                trade.getInvestmentAccount().getId(),
                trade.getStock().getId(),
                trade.getStock().getStockCode(),
                trade.getStock().getStockName(),
                trade.getTradeType(),
                trade.getQuantity(),
                trade.getExecutionPrice(),
                trade.getTotalAmount(),
                trade.getRequestedPrice(),
                trade.getPriceDeviationBps(),
                LocalDateTime.ofInstant(trade.getSnapshotAt(), SEOUL_ZONE),
                LocalDateTime.ofInstant(trade.getExecutedAt(), SEOUL_ZONE)
        );
    }
}
