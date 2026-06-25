package com.team10.backend.domain.investment.realtime.service.kis;

import com.team10.backend.domain.investment.client.realtime.KisOrderbookWebSocketClient;
import com.team10.backend.domain.investment.realtime.repository.RealtimeOrderbookSubscriptionStore;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeOrderbookKisSubscriptionCoordinator {

    private final RealtimeOrderbookSubscriptionStore subscriptionStore;
    private final KisOrderbookWebSocketClient kisOrderbookWebSocketClient;

    /**
     * Redis의 활성 stream 상태를 기준으로 특정 종목의 KIS WebSocket 구독 상태를 맞춘다.
     */
    public void reconcileStock(String stockCode) {
        validateStockCode(stockCode);

        long activeStreamCount = subscriptionStore.countActiveStreamsByStockCode(stockCode);
        boolean currentlySubscribed = kisOrderbookWebSocketClient.subscribedStockCodes().contains(stockCode);

        if (activeStreamCount > 0) {
            if (!currentlySubscribed && kisOrderbookWebSocketClient.subscribe(stockCode)) {
                log.info("KIS orderbook subscription started. stockCode={}, activeStreamCount={}",
                        stockCode,
                        activeStreamCount);
            }
            return;
        }

        if (currentlySubscribed && kisOrderbookWebSocketClient.unsubscribe(stockCode)) {
            log.info("KIS orderbook subscription ended. stockCode={}", stockCode);
        }
    }

    /**
     * Redis 전체 활성 종목 목록과 KIS WebSocket의 현재 구독 목록을 비교해 누락/잔여 구독을 보정한다.
     */
    public void reconcileAll() {
        Set<String> activeStockCodes = subscriptionStore.findActiveStockCodes();
        Set<String> subscribedStockCodes = kisOrderbookWebSocketClient.subscribedStockCodes();

        // Redis에서는 구독된 상태인데 WebSocketClient에서는 구독이 안된 경우 -> KIS WebSocket 구독 추가
        for (String stockCode : activeStockCodes) {
            if (!subscribedStockCodes.contains(stockCode) && kisOrderbookWebSocketClient.subscribe(stockCode)) {
                log.info("누락된 WebSocket 연결 재개. stockCode={}", stockCode);
            }
        }

        // WebSocketClient에서는 구독된 상태인데 Redis에서는 구독이 안된 경우 -> KIS WebSocket 구독 취소
        for (String stockCode : subscribedStockCodes) {
            if (!activeStockCodes.contains(stockCode) && kisOrderbookWebSocketClient.unsubscribe(stockCode)) {
                log.info("누락된 WebSocket 연결 제거. stockCode={}", stockCode);
            }
        }
    }

    private void validateStockCode(String stockCode) {
        if (!StringUtils.hasText(stockCode)) {
            throw new IllegalArgumentException("stockCode must not be blank");
        }
    }
}
