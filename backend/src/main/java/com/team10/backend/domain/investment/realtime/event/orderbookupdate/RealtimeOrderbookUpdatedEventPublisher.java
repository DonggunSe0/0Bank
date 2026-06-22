package com.team10.backend.domain.investment.realtime.event.orderbookupdate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeOrderbookUpdatedEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(RealtimeOrderbookSnapshot snapshot) {
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
