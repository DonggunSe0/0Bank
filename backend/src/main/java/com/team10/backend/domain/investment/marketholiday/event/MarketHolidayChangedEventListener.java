package com.team10.backend.domain.investment.marketholiday.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.marketholiday.service.MarketHolidaySyncService;
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
public class MarketHolidayChangedEventListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final MarketHolidaySyncService marketHolidaySyncService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            MarketHolidayChangedEvent event = objectMapper.readValue(
                    message.getBody(),
                    MarketHolidayChangedEvent.class
            );
            handle(event);
        } catch (IOException e) {
            log.warn("Failed to parse market holiday changed event. payload={}",
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    e);
        } catch (RuntimeException e) {
            log.warn("Failed to handle market holiday changed event. payload={}",
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    e);
        }
    }

    private void handle(MarketHolidayChangedEvent event) {
        if (event == null || event.marketType() == null) {
            log.warn("Ignore market holiday changed event without marketType. event={}", event);
            return;
        }

        marketHolidaySyncService.loadCacheFromDatabase(event.marketType());
        log.info("Market holiday cache reloaded after changed event. marketType={}", event.marketType());
    }
}
