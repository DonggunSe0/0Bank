package com.team10.backend.domain.investment.realtime.dto;

public record RealtimeOrderbookSubscription(
        String streamId,
        Long userId,
        String stockCode,
        String ownerInstanceId
) {
}
