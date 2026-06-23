package com.team10.backend.domain.investment.realtime.event.subcriptionchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.realtime.service.kis.RealtimeOrderbookKisLeaderService;
import com.team10.backend.domain.investment.realtime.service.stream.RealtimeOrderbookSseEmitterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeOrderbookSubscriptionChangedEventListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RealtimeOrderbookSseEmitterRegistry emitterRegistry;
    private final RealtimeOrderbookKisLeaderService kisLeaderService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RealtimeOrderbookSubscriptionChangedEvent event = objectMapper.readValue(
                    message.getBody(),
                    RealtimeOrderbookSubscriptionChangedEvent.class
            );
            handle(event);
        } catch (IOException e) {
            log.warn("Failed to parse realtime orderbook subscription changed event. payload={}",
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    e);
        } catch (RuntimeException e) {
            log.warn("Failed to handle realtime orderbook subscription changed event. payload={}",
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    e);
        }
    }

    private void handle(RealtimeOrderbookSubscriptionChangedEvent event) {
        // 구독 종료 시 로컬 SSE 연결 정리
        if (event.eventType() == RealtimeOrderbookSubscriptionChangedEvent.EventType.ENDED) {
            emitterRegistry.complete(event.streamId());
        }

        // Redis의 정보를 기반으로 WebSocket 연결 수정
        kisLeaderService.reconcileStockIfLeader(event.stockCode());
    }
}
