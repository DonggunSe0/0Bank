package com.team10.backend.domain.investment.portfolio.dto.res;

import com.team10.backend.domain.investment.portfolio.entity.InvestmentHolding;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import java.math.BigDecimal;

public record InvestmentHoldingRes(
        Long id,
        Long stockId,
        String stockCode,
        String stockName,
        StockMarket market,
        StockStatus status,
        Long quantity,
        BigDecimal averagePrice,
        Long marketCap,
        Long previousVolume
) {
    public static InvestmentHoldingRes from(InvestmentHolding holding) {
        Stock stock = holding.getStock();
        return new InvestmentHoldingRes(
                holding.getId(),
                stock.getId(),
                stock.getStockCode(),
                stock.getStockName(),
                stock.getMarket(),
                stock.getStatus(),
                holding.getQuantity(),
                holding.getAveragePrice(),
                stock.getMarketCap(),
                stock.getPreviousVolume()
        );
    }
}
