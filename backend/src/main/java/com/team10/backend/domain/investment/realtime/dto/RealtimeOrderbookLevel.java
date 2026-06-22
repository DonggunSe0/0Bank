package com.team10.backend.domain.investment.realtime.dto;

public record RealtimeOrderbookLevel(
        int level,
        Long price,
        Long quantity
) {
}
