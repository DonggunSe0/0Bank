package com.team10.backend.domain.investment.realtime.event;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants;
import com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookSseConstants;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookLevel;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import com.team10.backend.domain.investment.realtime.event.orderbookupdate.RealtimeOrderbookUpdatedEventListener;
import com.team10.backend.domain.investment.realtime.service.stream.RealtimeOrderbookSseEmitterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.DefaultMessage;

class RealtimeOrderbookUpdatedEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RealtimeOrderbookSseEmitterRegistry emitterRegistry =
            Mockito.mock(RealtimeOrderbookSseEmitterRegistry.class);
    private final RealtimeOrderbookUpdatedEventListener listener =
            new RealtimeOrderbookUpdatedEventListener(objectMapper, emitterRegistry);

    @Test
    @DisplayName("호가 갱신 이벤트를 받으면 동일 종목을 구독 중인 로컬 SSE stream에 orderbook-updated 이벤트를 전송한다")
    void handleOrderbookUpdatedEvent() throws Exception {
        RealtimeOrderbookSnapshot snapshot = snapshot("005930");

        listener.onMessage(message(snapshot), null);

        verify(emitterRegistry).sendOrderbookUpdateToSubscribers(
                "005930",
                RealtimeOrderbookSseConstants.ORDERBOOK_UPDATED_EVENT_NAME,
                snapshot
        );
    }

    @Test
    @DisplayName("파싱할 수 없는 호가 갱신 이벤트는 로컬 SSE stream에 전송하지 않는다")
    void ignoreInvalidOrderbookUpdatedEvent() {
        DefaultMessage message = new DefaultMessage(
                RealtimeOrderbookRedisConstants.ORDERBOOK_UPDATED_CHANNEL.getBytes(StandardCharsets.UTF_8),
                "not-json".getBytes(StandardCharsets.UTF_8)
        );

        listener.onMessage(message, null);

        verify(emitterRegistry, never()).sendOrderbookUpdateToSubscribers(Mockito.any(), Mockito.any(), Mockito.any());
    }

    private DefaultMessage message(RealtimeOrderbookSnapshot snapshot) throws Exception {
        return new DefaultMessage(
                RealtimeOrderbookRedisConstants.ORDERBOOK_UPDATED_CHANNEL.getBytes(StandardCharsets.UTF_8),
                objectMapper.writeValueAsBytes(snapshot)
        );
    }

    private RealtimeOrderbookSnapshot snapshot(String stockCode) {
        return new RealtimeOrderbookSnapshot(
                stockCode,
                "145856",
                "0",
                List.of(new RealtimeOrderbookLevel(1, 358500L, 53949L)),
                List.of(new RealtimeOrderbookLevel(1, 358000L, 40154L)),
                796206L,
                227494L
        );
    }
}
