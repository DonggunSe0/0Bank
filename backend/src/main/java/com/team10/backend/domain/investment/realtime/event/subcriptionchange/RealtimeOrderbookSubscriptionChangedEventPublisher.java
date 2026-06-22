package com.team10.backend.domain.investment.realtime.event.subcriptionchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeOrderbookSubscriptionChangedEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(RealtimeOrderbookSubscriptionChangedEvent event) {
        try {
            redisTemplate.convertAndSend(
                    RealtimeOrderbookRedisConstants.SUBSCRIPTION_CHANGED_CHANNEL,
                    objectMapper.writeValueAsString(event)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("실시간 호가 구독 변경 이벤트 직렬화에 실패했습니다.", e);
        }
    }
}
