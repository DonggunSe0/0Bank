package com.team10.backend.domain.investment.realtime.event.orderbookupdate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import com.team10.backend.domain.investment.realtime.repository.RealtimeOrderbookSnapshotStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeOrderbookUpdatedEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RealtimeOrderbookSnapshotStore snapshotStore;

    public void publish(RealtimeOrderbookSnapshot snapshot) {
        try {
            snapshotStore.save(snapshot);
        } catch (RuntimeException e) {
            log.warn("Failed to save realtime orderbook snapshot. stockCode={}",
                    snapshot == null ? null : snapshot.stockCode(),
                    e);
        }

        try {
            redisTemplate.convertAndSend(
                    RealtimeOrderbookRedisConstants.ORDERBOOK_UPDATED_CHANNEL,
                    objectMapper.writeValueAsString(snapshot)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("실시간 호가 갱신 이벤트 직렬화에 실패했습니다.", e);
        }
    }
}
