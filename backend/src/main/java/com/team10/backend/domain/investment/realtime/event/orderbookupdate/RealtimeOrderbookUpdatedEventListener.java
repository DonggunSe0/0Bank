package com.team10.backend.domain.investment.realtime.event.orderbookupdate;

import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookSseConstants.ORDERBOOK_UPDATED_EVENT_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import com.team10.backend.domain.investment.realtime.service.stream.RealtimeOrderbookSseEmitterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeOrderbookUpdatedEventListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RealtimeOrderbookSseEmitterRegistry emitterRegistry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RealtimeOrderbookSnapshot snapshot = objectMapper.readValue(
                    message.getBody(),
                    RealtimeOrderbookSnapshot.class
            );
            handle(snapshot);
        } catch (IOException e) {
            log.warn("Failed to parse realtime orderbook updated event. payload={}",
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    e);
        }
    }

    private void handle(RealtimeOrderbookSnapshot snapshot) {
        if (snapshot == null || !StringUtils.hasText(snapshot.stockCode())) {
            log.warn("Ignore realtime orderbook updated event without stockCode. snapshot={}", snapshot);
            return;
        }

        int sentCount = emitterRegistry.sendOrderbookUpdateToSubscribers(
                snapshot.stockCode(),
                ORDERBOOK_UPDATED_EVENT_NAME,
                snapshot
        );

        log.debug("Realtime orderbook update sent to local SSE streams. stockCode={}, sentCount={}",
                snapshot.stockCode(),
                sentCount);
    }
}
