package com.team10.backend.domain.investment.marketholiday.config;

import com.team10.backend.domain.investment.marketholiday.event.MarketHolidayChangedEventListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "investment.market-holiday.redis-pub-sub",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MarketHolidayRedisPubSubConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final MarketHolidayChangedEventListener marketHolidayChangedEventListener;

    @Bean
    public RedisMessageListenerContainer marketHolidayRedisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                marketHolidayChangedEventListener,
                ChannelTopic.of(MarketHolidayRedisConstants.HOLIDAY_CHANGED_CHANNEL)
        );
        return container;
    }
}
