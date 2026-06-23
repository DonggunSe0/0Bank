package com.team10.backend.domain.investment.client.stock.dto;

import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import java.time.LocalDate;

public record KisStockMasterRow(
        String stockCode,
        String standardCode,
        String stockName,
        StockMarket market,
        CurrencyCode currencyCode,
        StockStatus status,
        LocalDate listedDate,
        Long capitalAmount,
        Long salesAmount,
        Long netIncome,
        Long marketCap,
        Long previousVolume
) {
}
