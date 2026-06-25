package com.team10.backend.domain.investment.trade.service;

import static com.team10.backend.domain.investment.config.KisConstants.SEOUL_ZONE;

import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import com.team10.backend.domain.investment.account.repository.InvestmentAccountRepository;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import com.team10.backend.domain.investment.marketholiday.util.MarketStatusValidator;
import com.team10.backend.domain.investment.portfolio.entity.InvestmentHolding;
import com.team10.backend.domain.investment.portfolio.repository.InvestmentHoldingRepository;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookPriceSnapshot;
import com.team10.backend.domain.investment.realtime.repository.RealtimeOrderbookSnapshotStore;
import com.team10.backend.domain.investment.realtime.repository.RealtimeOrderbookSubscription;
import com.team10.backend.domain.investment.realtime.repository.RealtimeOrderbookSubscriptionStore;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.repository.StockRepository;
import com.team10.backend.domain.investment.trade.dto.req.MarketOrderCreateReq;
import com.team10.backend.domain.investment.trade.dto.res.InvestmentTradeRes;
import com.team10.backend.domain.investment.trade.entity.InvestmentTrade;
import com.team10.backend.domain.investment.trade.repository.InvestmentTradeRepository;
import com.team10.backend.domain.investment.trade.type.InvestmentTradeType;
import com.team10.backend.global.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InvestmentTradeBusinessService {

    private static final int DEFAULT_MAX_DEVIATION_BPS = 100;
    private static final Duration SNAPSHOT_MAX_AGE = Duration.ofSeconds(3);

    private final InvestmentAccountRepository investmentAccountRepository;
    private final StockRepository stockRepository;
    private final InvestmentHoldingRepository investmentHoldingRepository;
    private final InvestmentTradeRepository investmentTradeRepository;
    private final RealtimeOrderbookSubscriptionStore subscriptionStore;
    private final RealtimeOrderbookSnapshotStore snapshotStore;
    private final MarketStatusValidator marketStatusValidator;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public InvestmentTradeRes executeMarketOrder(
            Long userId,
            String idempotencyKey,
            MarketOrderCreateReq request
    ) {
        validateQuantity(request.quantity());

        InvestmentAccount account = investmentAccountRepository
                .findByIdAndUserIdForUpdate(request.accountId(), userId)
                .orElseThrow(() -> new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_FOUND));

        account.verifyPassword(passwordEncoder, request.accountPassword());
        validateActiveAccount(account);

        Stock stock = stockRepository.findById(request.stockId())
                .orElseThrow(() -> new BusinessException(InvestmentErrorCode.STOCK_NOT_FOUND));
        validateTradableStock(stock);

        validateMarketOpen();

        validateActiveRealtimeSubscription(userId, stock.getStockCode(), request.streamId());

        InvestmentHolding holding = investmentHoldingRepository
                .findByInvestmentAccountIdAndStockIdForUpdate(account.getId(), stock.getId())
                .orElse(null);

        RealtimeOrderbookPriceSnapshot snapshot = snapshotStore.findByStockCode(stock.getStockCode())
                .orElseThrow(() -> new BusinessException(InvestmentErrorCode.ORDER_PRICE_UNAVAILABLE));
        validateFreshSnapshot(snapshot);

        Long executionPrice = resolveExecutionPrice(request.tradeType(), snapshot);
        validatePriceDeviation(request.tradeType(), executionPrice, request.expectedPrice());

        Long totalAmount = multiplyExact(executionPrice, request.quantity());
        Integer priceDeviationBps = calculatePriceDeviationBps(executionPrice, request.expectedPrice());
        Instant executedAt = Instant.now();

        validateExecutable(account, holding, request.tradeType(), request.quantity(), totalAmount);

        InvestmentTrade trade = saveTrade(
                account,
                stock,
                request,
                executionPrice,
                totalAmount,
                priceDeviationBps,
                snapshot.receivedAt(),
                idempotencyKey,
                executedAt
        );

        applyTrade(account, stock, holding, request.tradeType(), request.quantity(), executionPrice, totalAmount);

        return InvestmentTradeRes.from(trade);
    }

    private void validateExecutable(
            InvestmentAccount account,
            InvestmentHolding holding,
            InvestmentTradeType tradeType,
            Long quantity,
            Long totalAmount
    ) {
        if (tradeType == InvestmentTradeType.BUY) {
            if (account.getCashBalance() < totalAmount) {
                throw new BusinessException(InvestmentErrorCode.INSUFFICIENT_CASH_BALANCE);
            }
            return;
        }

        // tradeType == InvestmentTradeType.SELL
        if (holding == null || holding.getQuantity() < quantity) {
            throw new BusinessException(InvestmentErrorCode.INSUFFICIENT_HOLDING_QUANTITY);
        }
    }

    private void applyTrade(
            InvestmentAccount account,
            Stock stock,
            InvestmentHolding holding,
            InvestmentTradeType tradeType,
            Long quantity,
            Long executionPrice,
            Long totalAmount
    ) {
        if (tradeType == InvestmentTradeType.BUY) {
            account.withdrawCash(totalAmount);
            if (holding == null) {
                investmentHoldingRepository.save(InvestmentHolding.create(
                        account,
                        stock,
                        quantity,
                        java.math.BigDecimal.valueOf(executionPrice).setScale(2)
                ));
                return;
            }
            holding.increase(quantity, executionPrice);
            return;
        }

        if (holding == null || holding.getQuantity() < quantity) {
            throw new BusinessException(InvestmentErrorCode.INSUFFICIENT_HOLDING_QUANTITY);
        }

        // tradeType == InvestmentTradeType.SELL
        holding.decrease(quantity);
        account.depositCash(totalAmount);
        if (holding.isEmpty()) {
            investmentHoldingRepository.delete(holding);
        }
    }

    private InvestmentTrade saveTrade(
            InvestmentAccount account,
            Stock stock,
            MarketOrderCreateReq request,
            Long executionPrice,
            Long totalAmount,
            Integer priceDeviationBps,
            Instant snapshotAt,
            String idempotencyKey,
            Instant executedAt
    ) {
        try {
            return investmentTradeRepository.saveAndFlush(InvestmentTrade.create(
                    account,
                    stock,
                    request.tradeType(),
                    request.quantity(),
                    executionPrice,
                    totalAmount,
                    request.expectedPrice(),
                    priceDeviationBps,
                    snapshotAt,
                    idempotencyKey,
                    executedAt
            ));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(InvestmentErrorCode.INVESTMENT_TRADE_DUPLICATED);
        }
    }

    private Long resolveExecutionPrice(
            InvestmentTradeType tradeType,
            RealtimeOrderbookPriceSnapshot snapshot
    ) {
        if (tradeType == InvestmentTradeType.BUY) {
            return validPrice(snapshot.bestAskPrice());
        }
        if (tradeType == InvestmentTradeType.SELL) {
            return validPrice(snapshot.bestBidPrice());
        }
        throw new BusinessException(InvestmentErrorCode.INVALID_ORDER_AMOUNT);
    }

    private Long validPrice(Long price) {
        if (price == null || price <= 0) {
            throw new BusinessException(InvestmentErrorCode.ORDER_PRICE_UNAVAILABLE);
        }
        return price;
    }

    private void validateFreshSnapshot(RealtimeOrderbookPriceSnapshot snapshot) {
        if (snapshot.receivedAt() == null
                || Duration.between(snapshot.receivedAt(), Instant.now()).compareTo(SNAPSHOT_MAX_AGE) > 0) {
            throw new BusinessException(InvestmentErrorCode.ORDER_PRICE_STALE);
        }
    }

    private void validatePriceDeviation(
            InvestmentTradeType tradeType,
            Long executionPrice,
            Long expectedPrice
    ) {
        if (expectedPrice == null || expectedPrice <= 0) {
            throw new BusinessException(InvestmentErrorCode.INVALID_ORDER_AMOUNT);
        }

        long deviationAmount = multiplyExact(expectedPrice, (long) DEFAULT_MAX_DEVIATION_BPS) / 10_000;
        long allowedPrice;
        try {
            allowedPrice = tradeType == InvestmentTradeType.BUY
                    ? Math.addExact(expectedPrice, deviationAmount)
                    : Math.subtractExact(expectedPrice, deviationAmount);
        } catch (ArithmeticException e) {
            throw new BusinessException(InvestmentErrorCode.INVALID_ORDER_AMOUNT);
        }

        if (tradeType == InvestmentTradeType.BUY && executionPrice > allowedPrice) {
            throw new BusinessException(InvestmentErrorCode.PRICE_DEVIATION_EXCEEDED);
        }
        if (tradeType == InvestmentTradeType.SELL && executionPrice < allowedPrice) {
            throw new BusinessException(InvestmentErrorCode.PRICE_DEVIATION_EXCEEDED);
        }
    }

    private Integer calculatePriceDeviationBps(Long executionPrice, Long expectedPrice) {
        try {
            long diff = Math.abs(Math.subtractExact(executionPrice, expectedPrice));
            return Math.toIntExact(Math.multiplyExact(diff, 10_000L) / expectedPrice);
        } catch (ArithmeticException e) {
            throw new BusinessException(InvestmentErrorCode.INVALID_ORDER_AMOUNT);
        }
    }

    private void validateActiveRealtimeSubscription(Long userId, String stockCode, String streamId) {
        if (!StringUtils.hasText(streamId)) {
            throw new BusinessException(InvestmentErrorCode.ORDER_REQUIRES_ACTIVE_REALTIME_SUBSCRIPTION);
        }

        RealtimeOrderbookSubscription subscription = subscriptionStore.findByStreamId(streamId)
                .orElseThrow(() -> new BusinessException(
                        InvestmentErrorCode.ORDER_REQUIRES_ACTIVE_REALTIME_SUBSCRIPTION
                ));

        if (!userId.equals(subscription.userId()) || !stockCode.equals(subscription.stockCode())) {
            throw new BusinessException(InvestmentErrorCode.ORDER_REQUIRES_ACTIVE_REALTIME_SUBSCRIPTION);
        }
    }

    private void validateMarketOpen() {
        if (!marketStatusValidator.isContinuousTradingTime(LocalDateTime.now(SEOUL_ZONE), MarketType.KRX)) {
            throw new BusinessException(InvestmentErrorCode.MARKET_CLOSED);
        }
    }

    private void validateActiveAccount(InvestmentAccount account) {
        if (!account.isActive()) {
            throw new BusinessException(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_ACTIVE);
        }
    }

    private void validateTradableStock(Stock stock) {
        if (!stock.isTradable()) {
            throw new BusinessException(InvestmentErrorCode.STOCK_NOT_TRADABLE);
        }
    }

    private void validateQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(InvestmentErrorCode.INVALID_ORDER_QUANTITY);
        }
    }

    private Long multiplyExact(Long left, Long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException e) {
            throw new BusinessException(InvestmentErrorCode.INVALID_ORDER_AMOUNT);
        }
    }
}
