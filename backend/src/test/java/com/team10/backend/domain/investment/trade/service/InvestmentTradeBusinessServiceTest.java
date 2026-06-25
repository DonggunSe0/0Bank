package com.team10.backend.domain.investment.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.trade.dto.req.MarketOrderCreateReq;
import com.team10.backend.domain.investment.trade.dto.res.InvestmentTradeRes;
import com.team10.backend.domain.investment.trade.entity.InvestmentTrade;
import com.team10.backend.domain.investment.trade.repository.InvestmentTradeRepository;
import com.team10.backend.domain.investment.trade.type.InvestmentTradeType;
import com.team10.backend.domain.investment.type.CurrencyCode;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentTradeBusinessServiceTest {

    @Mock
    private InvestmentAccountRepository investmentAccountRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private InvestmentHoldingRepository investmentHoldingRepository;

    @Mock
    private InvestmentTradeRepository investmentTradeRepository;

    @Mock
    private RealtimeOrderbookSubscriptionStore subscriptionStore;

    @Mock
    private RealtimeOrderbookSnapshotStore snapshotStore;

    @Mock
    private MarketStatusValidator marketStatusValidator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private InvestmentTradeBusinessService service;

    private User user;
    private InvestmentAccount account;
    private Stock stock;

    @BeforeEach
    void setUp() {
        user = User.create("test@example.com", "encoded-user-password", "테스터", "01012345678",
                LocalDate.of(1995, 1, 1));
        ReflectionTestUtils.setField(user, "id", 1L);

        account = InvestmentAccount.create(user, "1234567890-12", "투자 계좌", "encoded-password", CurrencyCode.KRW);
        ReflectionTestUtils.setField(account, "id", 10L);
        account.depositCash(1_000_000L);

        stock = Stock.create(
                "005930",
                "KR7005930003",
                "삼성전자",
                StockMarket.KOSPI,
                CurrencyCode.KRW,
                StockStatus.ACTIVE,
                LocalDate.of(1975, 6, 11),
                null,
                null,
                null,
                4_000_000L,
                10_000L
        );
        ReflectionTestUtils.setField(stock, "id", 20L);
    }

    @Test
    @DisplayName("매수 주문은 최우선 매도호가로 전량 체결하고 예수금과 보유 수량을 갱신한다")
    void buySuccess() {
        MarketOrderCreateReq request = request(InvestmentTradeType.BUY, 2L, 70_000L);
        givenCommon(request, Optional.empty(), snapshot(70_000L, 69_900L, Instant.now()));
        when(investmentTradeRepository.saveAndFlush(any(InvestmentTrade.class))).thenAnswer(invocation -> {
            InvestmentTrade trade = invocation.getArgument(0);
            ReflectionTestUtils.setField(trade, "id", 100L);
            return trade;
        });

        InvestmentTradeRes response = service.executeMarketOrder(1L, "order-key-1", request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.tradeType()).isEqualTo(InvestmentTradeType.BUY);
        assertThat(response.executionPrice()).isEqualTo(70_000L);
        assertThat(response.totalAmount()).isEqualTo(140_000L);
        assertThat(account.getCashBalance()).isEqualTo(860_000L);

        ArgumentCaptor<InvestmentHolding> holdingCaptor = ArgumentCaptor.forClass(InvestmentHolding.class);
        verify(investmentHoldingRepository).save(holdingCaptor.capture());
        assertThat(holdingCaptor.getValue().getQuantity()).isEqualTo(2L);
        assertThat(holdingCaptor.getValue().getAveragePrice()).isEqualByComparingTo("70000.00");
    }

    @Test
    @DisplayName("매도 주문은 최우선 매수호가로 전량 체결하고 예수금과 보유 수량을 갱신한다")
    void sellSuccess() {
        InvestmentHolding holding = holding(5L, "70000.00");
        MarketOrderCreateReq request = request(InvestmentTradeType.SELL, 2L, 70_000L);
        givenCommon(request, Optional.of(holding), snapshot(70_100L, 70_000L, Instant.now()));
        when(investmentTradeRepository.saveAndFlush(any(InvestmentTrade.class))).thenAnswer(invocation -> {
            InvestmentTrade trade = invocation.getArgument(0);
            ReflectionTestUtils.setField(trade, "id", 101L);
            return trade;
        });

        InvestmentTradeRes response = service.executeMarketOrder(1L, "order-key-2", request);

        assertThat(response.tradeType()).isEqualTo(InvestmentTradeType.SELL);
        assertThat(response.executionPrice()).isEqualTo(70_000L);
        assertThat(account.getCashBalance()).isEqualTo(1_140_000L);
        assertThat(holding.getQuantity()).isEqualTo(3L);
        verify(investmentHoldingRepository, never()).delete(holding);
    }

    @Test
    @DisplayName("Redis 스냅샷이 없으면 주문이 실패한다")
    void snapshotUnavailable() {
        MarketOrderCreateReq request = request(InvestmentTradeType.BUY, 1L, 70_000L);
        givenCommonBeforeSnapshot(request, Optional.empty());
        when(snapshotStore.findByStockCode("005930")).thenReturn(Optional.empty());

        assertBusinessException(request, InvestmentErrorCode.ORDER_PRICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Redis 스냅샷이 3초보다 오래되면 주문이 실패한다")
    void snapshotStale() {
        MarketOrderCreateReq request = request(InvestmentTradeType.BUY, 1L, 70_000L);
        givenCommon(request, Optional.empty(), snapshot(70_000L, 69_900L, Instant.now().minusSeconds(4)));

        assertBusinessException(request, InvestmentErrorCode.ORDER_PRICE_STALE);
    }

    @Test
    @DisplayName("매수 주문 금액보다 예수금이 부족하면 주문이 실패한다")
    void insufficientCash() {
        ReflectionTestUtils.setField(account, "cashBalance", 50_000L);
        MarketOrderCreateReq request = request(InvestmentTradeType.BUY, 1L, 70_000L);
        givenCommon(request, Optional.empty(), snapshot(70_000L, 69_900L, Instant.now()));

        assertBusinessException(request, InvestmentErrorCode.INSUFFICIENT_CASH_BALANCE);
    }

    @Test
    @DisplayName("매도할 보유 수량이 부족하면 주문이 실패한다")
    void insufficientHoldingQuantity() {
        InvestmentHolding holding = holding(1L, "70000.00");
        MarketOrderCreateReq request = request(InvestmentTradeType.SELL, 2L, 70_000L);
        givenCommon(request, Optional.of(holding), snapshot(70_100L, 70_000L, Instant.now()));

        assertBusinessException(request, InvestmentErrorCode.INSUFFICIENT_HOLDING_QUANTITY);
    }

    @Test
    @DisplayName("체결 가격이 기대 가격 대비 허용 편차를 초과하면 주문이 실패한다")
    void priceDeviationExceeded() {
        MarketOrderCreateReq request = request(InvestmentTradeType.BUY, 1L, 70_000L);
        givenCommon(request, Optional.empty(), snapshot(70_800L, 70_000L, Instant.now()));

        assertBusinessException(request, InvestmentErrorCode.PRICE_DEVIATION_EXCEEDED);
    }

    @Test
    @DisplayName("사용자가 해당 종목 실시간 호가를 구독 중이지 않으면 주문이 실패한다")
    void noActiveRealtimeSubscription() {
        MarketOrderCreateReq request = request(InvestmentTradeType.BUY, 1L, 70_000L);
        givenCommonBeforeSubscription(request);
        when(subscriptionStore.findByStreamId("stream-1")).thenReturn(Optional.empty());

        assertBusinessException(request, InvestmentErrorCode.ORDER_REQUIRES_ACTIVE_REALTIME_SUBSCRIPTION);
    }

    @Test
    @DisplayName("streamId가 다른 사용자의 구독이면 주문이 실패한다")
    void realtimeSubscriptionUserMismatch() {
        MarketOrderCreateReq request = request(InvestmentTradeType.BUY, 1L, 70_000L);
        givenCommonBeforeSubscription(request);
        when(subscriptionStore.findByStreamId("stream-1"))
                .thenReturn(Optional.of(subscription(2L, "005930")));

        assertBusinessException(request, InvestmentErrorCode.ORDER_REQUIRES_ACTIVE_REALTIME_SUBSCRIPTION);
    }

    @Test
    @DisplayName("streamId가 다른 종목의 구독이면 주문이 실패한다")
    void realtimeSubscriptionStockMismatch() {
        MarketOrderCreateReq request = request(InvestmentTradeType.BUY, 1L, 70_000L);
        givenCommonBeforeSubscription(request);
        when(subscriptionStore.findByStreamId("stream-1"))
                .thenReturn(Optional.of(subscription(1L, "000660")));

        assertBusinessException(request, InvestmentErrorCode.ORDER_REQUIRES_ACTIVE_REALTIME_SUBSCRIPTION);
    }

    @Test
    @DisplayName("매도 후 수량이 0이면 보유 종목 스냅샷을 삭제한다")
    void deleteEmptyHoldingAfterSell() {
        InvestmentHolding holding = holding(2L, "70000.00");
        MarketOrderCreateReq request = request(InvestmentTradeType.SELL, 2L, 70_000L);
        givenCommon(request, Optional.of(holding), snapshot(70_100L, 70_000L, Instant.now()));
        when(investmentTradeRepository.saveAndFlush(any(InvestmentTrade.class))).thenAnswer(invocation -> {
            InvestmentTrade trade = invocation.getArgument(0);
            ReflectionTestUtils.setField(trade, "id", 102L);
            return trade;
        });

        service.executeMarketOrder(1L, "order-key-3", request);

        assertThat(holding.getQuantity()).isZero();
        verify(investmentHoldingRepository).delete(holding);
    }

    private void assertBusinessException(MarketOrderCreateReq request, InvestmentErrorCode errorCode) {
        assertThatThrownBy(() -> service.executeMarketOrder(1L, "order-key", request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);

        verify(investmentTradeRepository, never()).saveAndFlush(any(InvestmentTrade.class));
    }

    private void givenCommon(
            MarketOrderCreateReq request,
            Optional<InvestmentHolding> holding,
            RealtimeOrderbookPriceSnapshot snapshot
    ) {
        givenCommonBeforeSnapshot(request, holding);
        when(snapshotStore.findByStockCode("005930")).thenReturn(Optional.of(snapshot));
    }

    private void givenCommonBeforeSnapshot(MarketOrderCreateReq request, Optional<InvestmentHolding> holding) {
        givenCommonBeforeSubscription(request);
        when(subscriptionStore.findByStreamId(request.streamId()))
                .thenReturn(Optional.of(subscription(1L, "005930")));
        when(investmentHoldingRepository.findByInvestmentAccountIdAndStockIdForUpdate(10L, 20L))
                .thenReturn(holding);
    }

    private void givenCommonBeforeSubscription(MarketOrderCreateReq request) {
        when(investmentAccountRepository.findByIdAndUserIdForUpdate(request.accountId(), 1L))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(stockRepository.findById(request.stockId())).thenReturn(Optional.of(stock));
        when(marketStatusValidator.isContinuousTradingTime(any(LocalDateTime.class), eq(MarketType.KRX)))
                .thenReturn(true);
    }

    private MarketOrderCreateReq request(InvestmentTradeType tradeType, Long quantity, Long expectedPrice) {
        return new MarketOrderCreateReq(
                10L,
                20L,
                "stream-1",
                tradeType,
                quantity,
                "123456",
                expectedPrice
        );
    }

    private RealtimeOrderbookSubscription subscription(Long userId, String stockCode) {
        return new RealtimeOrderbookSubscription("stream-1", userId, stockCode, "instance-a");
    }

    private RealtimeOrderbookPriceSnapshot snapshot(Long askPrice, Long bidPrice, Instant receivedAt) {
        return new RealtimeOrderbookPriceSnapshot("005930", askPrice, bidPrice, receivedAt);
    }

    private InvestmentHolding holding(Long quantity, String averagePrice) {
        InvestmentHolding holding = InvestmentHolding.create(
                account,
                stock,
                quantity,
                new BigDecimal(averagePrice)
        );
        ReflectionTestUtils.setField(holding, "id", 30L);
        return holding;
    }
}
