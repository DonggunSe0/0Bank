package com.team10.backend.domain.investment.stock.entity;

import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "stocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_stocks_stock_code", columnNames = "stock_code")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity {

    /**
     * 단축코드
     */
    @Column(name = "stock_code", nullable = false, unique = true, length = 20)
    private String stockCode;

    /**
     * 표준코드
     */
    @Column(nullable = false, length = 20)
    private String standardCode;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockMarket market; // 코스피 | 코스닥 ...

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockStatus status;

    /**
     * 주식 상장 일자 (MST: stck_lstn_date)
     */
    @Column(name = "listed_date")
    private LocalDate listedDate;

    /**
     * 자본금 (MST: cpfn)
     */
    @Column(name = "capital_amount")
    private Long capitalAmount;

    /**
     * 매출액 (MST: sale_account)
     */
    @Column(name = "sales_amount")
    private Long salesAmount;

    /**
     * 당기순이익 (MST: thtr_ntin)
     */
    @Column(name = "net_income")
    private Long netIncome;

    /**
     * 전일 기준 시가총액(억) (MST: prdy_avls_scal)
     */
    @Column(name = "market_cap")
    private Long marketCap;

    /**
     * 전일 거래량 (MST: prdy_vol)
     */
    @Column(name = "previous_volume")
    private Long previousVolume;

    public static Stock create(
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
        Stock stock = new Stock();
        stock.stockCode = stockCode;
        stock.standardCode = standardCode;
        stock.stockName = stockName;
        stock.market = market;
        stock.currencyCode = currencyCode;
        stock.status = status;
        stock.listedDate = listedDate;
        stock.capitalAmount = capitalAmount;
        stock.salesAmount = salesAmount;
        stock.netIncome = netIncome;
        stock.marketCap = marketCap;
        stock.previousVolume = previousVolume;
        return stock;
    }

    public void updateMaster(
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
        this.standardCode = standardCode;
        this.stockName = stockName;
        this.market = market;
        this.currencyCode = currencyCode;
        this.status = status;
        this.listedDate = listedDate;
        this.capitalAmount = capitalAmount;
        this.salesAmount = salesAmount;
        this.netIncome = netIncome;
        this.marketCap = marketCap;
        this.previousVolume = previousVolume;
    }

    public boolean isTradable() {
        return status == StockStatus.ACTIVE;
    }
}
