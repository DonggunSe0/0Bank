package com.team10.backend.domain.investment.watchlist.service;

import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.stock.dto.res.StockSummaryRes;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.repository.StockRepository;
import com.team10.backend.domain.investment.watchlist.entity.StockWatchlist;
import com.team10.backend.domain.investment.watchlist.repository.StockWatchlistRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockWatchlistService {

    private static final int MAX_WATCHLIST_COUNT = 20;

    private final StockWatchlistRepository stockWatchlistRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    @Transactional
    public StockSummaryRes addWatchlist(Long userId, Long stockId) {
        User user = getUserForUpdate(userId);
        Stock stock = getActiveStock(stockId);

        if (stockWatchlistRepository.existsByUserIdAndStockId(userId, stockId)) {
            throw new BusinessException(InvestmentErrorCode.WATCHLIST_DUPLICATED);
        }

        if (stockWatchlistRepository.countByUserId(userId) >= MAX_WATCHLIST_COUNT) {
            throw new BusinessException(InvestmentErrorCode.WATCHLIST_LIMIT_EXCEEDED);
        }

        try {
            // flush를 통해 즉시 쿼리를 수행. try - catch 내에서 유니크 예외가 발생하도록 강제한다
            stockWatchlistRepository.saveAndFlush(StockWatchlist.create(user, stock));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(InvestmentErrorCode.WATCHLIST_DUPLICATED);
        }

        return StockSummaryRes.from(stock);
    }

    public List<StockSummaryRes> getWatchlists(Long userId) {
        return stockWatchlistRepository.findAllByUserIdWithStock(userId).stream()
                .map(StockWatchlist::getStock)
                .map(StockSummaryRes::from)
                .toList();
    }

    @Transactional
    public void removeWatchlist(Long userId, Long stockId) {
        stockWatchlistRepository.deleteByUserIdAndStockId(userId, stockId);
    }

    private User getUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private Stock getActiveStock(Long stockId) {
        if (stockId == null) {
            throw new BusinessException(InvestmentErrorCode.STOCK_NOT_FOUND);
        }

        return stockRepository.findById(stockId)
                .filter(Stock::isTradable)
                .orElseThrow(() -> new BusinessException(InvestmentErrorCode.STOCK_NOT_FOUND));
    }
}
