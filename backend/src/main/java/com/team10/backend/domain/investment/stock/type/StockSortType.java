package com.team10.backend.domain.investment.stock.type;

public enum StockSortType {
    STOCK_NAME("stockName"),
    STOCK_CODE("stockCode"),
    MARKET_CAP("marketCap"),
    SALES_AMOUNT("salesAmount"),
    NET_INCOME("netIncome"),
    PREVIOUS_VOLUME("previousVolume");

    private final String property;

    StockSortType(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
