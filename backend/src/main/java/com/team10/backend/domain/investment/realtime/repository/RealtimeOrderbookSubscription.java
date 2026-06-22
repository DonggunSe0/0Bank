package com.team10.backend.domain.investment.realtime.repository;

public record RealtimeOrderbookSubscription(
        String streamId,
        Long userId,
        String stockCode,
        String ownerInstanceId
) {
}
