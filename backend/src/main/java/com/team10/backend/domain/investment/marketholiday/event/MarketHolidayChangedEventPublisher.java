package com.team10.backend.domain.investment.marketholiday.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.marketholiday.config.MarketHolidayRedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketHolidayChangedEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(MarketHolidayChangedEvent event) {
        try {
            redisTemplate.convertAndSend(
                    MarketHolidayRedisConstants.HOLIDAY_CHANGED_CHANNEL,
                    objectMapper.writeValueAsString(event)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("휴장일 변경 이벤트 직렬화에 실패했습니다.", e);
        }
    }
}
