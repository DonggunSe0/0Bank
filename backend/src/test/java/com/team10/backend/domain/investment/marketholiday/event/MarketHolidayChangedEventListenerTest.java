package com.team10.backend.domain.investment.marketholiday.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.marketholiday.service.MarketHolidaySyncService;
import com.team10.backend.domain.investment.marketholiday.type.MarketType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

class MarketHolidayChangedEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MarketHolidaySyncService marketHolidaySyncService =
            org.mockito.Mockito.mock(MarketHolidaySyncService.class);
    private final MarketHolidayChangedEventListener listener =
            new MarketHolidayChangedEventListener(objectMapper, marketHolidaySyncService);

    @Test
    @DisplayName("휴장일 변경 이벤트를 수신하면 DB 기준으로 로컬 캐시를 다시 로딩한다")
    void reloadCacheFromDatabaseWhenChangedEventReceived() throws Exception {
        Message message = org.mockito.Mockito.mock(Message.class);
        when(message.getBody()).thenReturn(
                objectMapper.writeValueAsString(MarketHolidayChangedEvent.changed(MarketType.KRX))
                        .getBytes(StandardCharsets.UTF_8)
        );

        listener.onMessage(message, null);

        verify(marketHolidaySyncService).loadCacheFromDatabase(MarketType.KRX);
    }
}
