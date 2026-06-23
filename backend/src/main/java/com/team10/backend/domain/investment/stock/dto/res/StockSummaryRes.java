package com.team10.backend.domain.investment.stock.dto.res;

import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;

public record StockSummaryRes(
        Long id,
        String stockCode,
        String stockName,
        StockMarket market,
        StockStatus status,
        Long marketCap,
        Long previousVolume
) {

    public static StockSummaryRes from(Stock stock) {
        return new StockSummaryRes(
                stock.getId(),
                stock.getStockCode(),
                stock.getStockName(),
                stock.getMarket(),
                stock.getStatus(),
                stock.getMarketCap(),
                stock.getPreviousVolume()
        );
    }
}
