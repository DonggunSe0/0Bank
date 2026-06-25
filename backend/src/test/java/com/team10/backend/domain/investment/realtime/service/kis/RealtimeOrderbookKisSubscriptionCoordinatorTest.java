package com.team10.backend.domain.investment.realtime.service.kis;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.client.realtime.KisOrderbookWebSocketClient;
import com.team10.backend.domain.investment.realtime.repository.RealtimeOrderbookSubscriptionStore;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeOrderbookKisSubscriptionCoordinatorTest {

    @Mock
    private RealtimeOrderbookSubscriptionStore subscriptionStore;

    @Mock
    private KisOrderbookWebSocketClient kisOrderbookWebSocketClient;

    private RealtimeOrderbookKisSubscriptionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new RealtimeOrderbookKisSubscriptionCoordinator(
                subscriptionStore,
                kisOrderbookWebSocketClient
        );
    }

    @Test
    @DisplayName("활성 stream이 존재하고 KIS 미구독 상태이면 해당 종목을 구독한다")
    void reconcileStockSubscribesWhenActiveStreamExists() {
        when(subscriptionStore.countActiveStreamsByStockCode("005930")).thenReturn(1L);
        when(kisOrderbookWebSocketClient.subscribedStockCodes()).thenReturn(Set.of());

        coordinator.reconcileStock("005930");

        verify(kisOrderbookWebSocketClient).subscribe("005930");
        verify(kisOrderbookWebSocketClient, never()).unsubscribe("005930");
    }

    @Test
    @DisplayName("활성 stream이 존재하지만 이미 KIS 구독 중이면 중복 구독하지 않는다")
    void reconcileStockSkipsDuplicateSubscribe() {
        when(subscriptionStore.countActiveStreamsByStockCode("005930")).thenReturn(2L);
        when(kisOrderbookWebSocketClient.subscribedStockCodes()).thenReturn(Set.of("005930"));

        coordinator.reconcileStock("005930");

        verify(kisOrderbookWebSocketClient, never()).subscribe("005930");
        verify(kisOrderbookWebSocketClient, never()).unsubscribe("005930");
    }

    @Test
    @DisplayName("활성 stream이 없고 KIS 구독 중이면 해당 종목을 구독 해지한다")
    void reconcileStockUnsubscribesWhenNoActiveStreamExists() {
        when(subscriptionStore.countActiveStreamsByStockCode("005930")).thenReturn(0L);
        when(kisOrderbookWebSocketClient.subscribedStockCodes()).thenReturn(Set.of("005930"));

        coordinator.reconcileStock("005930");

        verify(kisOrderbookWebSocketClient).unsubscribe("005930");
        verify(kisOrderbookWebSocketClient, never()).subscribe("005930");
    }

    @Test
    @DisplayName("전체 reconcile은 Redis 활성 종목은 구독하고 stale KIS 구독은 해지한다")
    void reconcileAllSubscribesMissingActiveStocksAndUnsubscribesStaleStocks() {
        when(subscriptionStore.findActiveStockCodes()).thenReturn(Set.of("005930", "000660"));
        when(kisOrderbookWebSocketClient.subscribedStockCodes()).thenReturn(Set.of("005930", "035420"));

        coordinator.reconcileAll();

        verify(kisOrderbookWebSocketClient).subscribe("000660");
        verify(kisOrderbookWebSocketClient).unsubscribe("035420");
        verify(kisOrderbookWebSocketClient, never()).subscribe("005930");
        verify(kisOrderbookWebSocketClient, never()).unsubscribe("005930");
    }
}
