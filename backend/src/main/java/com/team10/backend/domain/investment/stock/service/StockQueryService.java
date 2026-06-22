package com.team10.backend.domain.investment.stock.service;

import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.stock.dto.res.StockDetailRes;
import com.team10.backend.domain.investment.stock.dto.res.StockSummaryRes;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.repository.StockRepository;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockSortType;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockQueryService {

    private static final int MIN_SEARCH_KEYWORD_LENGTH = 2;
    private static final StockMarket DEFAULT_MARKET = StockMarket.KOSPI;
    private static final StockStatus DEFAULT_STATUS = StockStatus.ACTIVE;
    private static final StockSortType DEFAULT_SORT = StockSortType.STOCK_NAME;
    private static final Sort.Direction DEFAULT_DIRECTION = Sort.Direction.ASC;

    private final StockRepository stockRepository;

    public List<StockSummaryRes> search(String keyword, StockMarket market) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (!StringUtils.hasText(normalizedKeyword)
                || normalizedKeyword.length() < MIN_SEARCH_KEYWORD_LENGTH) {
            return List.of();
        }

        String escapedKeyword = escapeLikeWildcards(normalizedKeyword);
        StockMarket targetMarket = market != null ? market : DEFAULT_MARKET;

        return stockRepository.search(
                        escapedKeyword,
                        targetMarket,
                        DEFAULT_STATUS
                ).stream()
                .map(StockSummaryRes::from)
                .toList();
    }

    public Page<StockSummaryRes> getStocks(
            StockMarket market,
            StockStatus status,
            StockSortType sort,
            Sort.Direction direction,
            int page,
            int size
    ) {
        StockMarket targetMarket = market != null ? market : DEFAULT_MARKET;
        StockStatus targetStatus = status != null ? status : DEFAULT_STATUS;
        StockSortType targetSort = sort != null ? sort : DEFAULT_SORT;
        Sort.Direction targetDirection = direction != null ? direction : DEFAULT_DIRECTION;

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                getSort(targetSort, targetDirection)
        );

        return stockRepository.findAllByMarketAndStatus(targetMarket, targetStatus, pageRequest)
                .map(StockSummaryRes::from);
    }

    public StockDetailRes getStock(String stockCode) {
        String normalizedStockCode = normalizeKeyword(stockCode);
        if (!StringUtils.hasText(normalizedStockCode)) {
            throw new BusinessException(InvestmentErrorCode.STOCK_NOT_FOUND);
        }

        Stock stock = stockRepository.findByStockCodeAndStatus(normalizedStockCode, DEFAULT_STATUS)
                .orElseThrow(() -> new BusinessException(InvestmentErrorCode.STOCK_NOT_FOUND));

        return StockDetailRes.from(stock);
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private String escapeLikeWildcards(String keyword) {
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private Sort getSort(StockSortType sort, Sort.Direction direction) {
        Sort result = Sort.by(direction, sort.property());

        if (sort != StockSortType.STOCK_NAME) {
            result = result.and(Sort.by(Sort.Direction.ASC, StockSortType.STOCK_NAME.property()));
        }

        if (sort != StockSortType.STOCK_CODE) {
            result = result.and(Sort.by(Sort.Direction.ASC, StockSortType.STOCK_CODE.property()));
        }

        return result;
    }
}
