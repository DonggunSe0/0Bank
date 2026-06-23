package com.team10.backend.domain.investment.watchlist.entity;

import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "stock_watchlists",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_watchlists_user_stock",
                columnNames = {"user_id", "stock_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockWatchlist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    public static StockWatchlist create(User user, Stock stock) {
        StockWatchlist watchlist = new StockWatchlist();
        watchlist.user = user;
        watchlist.stock = stock;
        return watchlist;
    }
}
