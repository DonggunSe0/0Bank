package com.team10.backend.domain.investment.realtime.config;

import com.team10.backend.domain.investment.realtime.event.orderbookupdate.RealtimeOrderbookUpdatedEventListener;
import com.team10.backend.domain.investment.realtime.event.subcriptionchange.RealtimeOrderbookSubscriptionChangedEventListener;
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
        prefix = "investment.realtime.redis-pub-sub",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RealtimeOrderbookRedisPubSubConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final RealtimeOrderbookSubscriptionChangedEventListener subscriptionChangedEventListener;
    private final RealtimeOrderbookUpdatedEventListener orderbookUpdatedEventListener;

    @Bean
    public RedisMessageListenerContainer realtimeOrderbookRedisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                subscriptionChangedEventListener,
                ChannelTopic.of(RealtimeOrderbookRedisConstants.SUBSCRIPTION_CHANGED_CHANNEL)
        );
        container.addMessageListener(
                orderbookUpdatedEventListener,
                ChannelTopic.of(RealtimeOrderbookRedisConstants.ORDERBOOK_UPDATED_CHANNEL)
        );
        return container;
    }
}
