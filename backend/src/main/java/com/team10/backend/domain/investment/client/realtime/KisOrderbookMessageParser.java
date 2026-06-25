package com.team10.backend.domain.investment.client.realtime;

import static com.team10.backend.domain.investment.config.KisConstants.RealtimeWebSocket.ORDERBOOK_TR_ID;

import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookLevel;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * KIS WebSocket으로 부터 수신되는 실시간 호가 데이터를 파싱한다
 *
 */
@Component
public class KisOrderbookMessageParser {

    private static final int ORDERBOOK_LEVEL_COUNT = 10;
    private static final int MIN_FIELD_COUNT = 45;

    private static final int STOCK_CODE_INDEX = 0;
    private static final int BUSINESS_TIME_INDEX = 1;
    private static final int TIME_TYPE_INDEX = 2;

    private static final int ASK_PRICE_START_INDEX = 3;
    private static final int BID_PRICE_START_INDEX = 13;
    private static final int ASK_QUANTITY_START_INDEX = 23;
    private static final int BID_QUANTITY_START_INDEX = 33;
    private static final int TOTAL_ASK_QUANTITY_INDEX = 43;
    private static final int TOTAL_BID_QUANTITY_INDEX = 44;

    public Optional<RealtimeOrderbookSnapshot> parse(String message) {
        if (!StringUtils.hasText(message)) {
            return Optional.empty();
        }

        String trimmedMessage = message.trim();
        if (isJsonMessage(trimmedMessage)) {
            return Optional.empty();
        }

        Optional<String> payload = extractPayload(trimmedMessage);
        if (payload.isEmpty()) {
            return Optional.empty();
        }

        String[] fields = payload.get().split("\\^", -1);
        if (fields.length < MIN_FIELD_COUNT) {
            return Optional.empty();
        }

        return Optional.of(new RealtimeOrderbookSnapshot(
                fields[STOCK_CODE_INDEX],
                fields[BUSINESS_TIME_INDEX],
                fields[TIME_TYPE_INDEX],
                parseLevels(fields, ASK_PRICE_START_INDEX, ASK_QUANTITY_START_INDEX),
                parseLevels(fields, BID_PRICE_START_INDEX, BID_QUANTITY_START_INDEX),
                parseLong(fields[TOTAL_ASK_QUANTITY_INDEX]),
                parseLong(fields[TOTAL_BID_QUANTITY_INDEX])
        ));
    }

    private boolean isJsonMessage(String message) {
        return message.startsWith("{") || message.startsWith("[");
    }

    private Optional<String> extractPayload(String message) {
        if (!message.contains("|")) {
            return Optional.of(message);
        }

        String[] parts = message.split("\\|", 4);
        if (parts.length < 4 || !ORDERBOOK_TR_ID.equals(parts[1])) {
            return Optional.empty();
        }

        return Optional.of(parts[3]);
    }

    private List<RealtimeOrderbookLevel> parseLevels(
            String[] fields,
            int priceStartIndex,
            int quantityStartIndex
    ) {
        List<RealtimeOrderbookLevel> levels = new ArrayList<>(ORDERBOOK_LEVEL_COUNT);

        for (int i = 0; i < ORDERBOOK_LEVEL_COUNT; i++) {
            levels.add(new RealtimeOrderbookLevel(
                    i + 1,
                    parseLong(fields[priceStartIndex + i]),
                    parseLong(fields[quantityStartIndex + i])
            ));
        }

        return List.copyOf(levels);
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return Long.valueOf(value.trim());
    }
}
